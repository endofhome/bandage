package handlers

import DateTimePatterns
import Logging.logger
import RouteMappings.play
import org.http4k.core.Headers
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.routing.path
import result.Result.Failure
import result.Result.Success
import result.map
import result.orElse
import storage.AudioTrackMetadata
import storage.AudioTrackMetadataEnhancer
import storage.FileStorage
import storage.MetadataStorage
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread


object Play {
    operator fun invoke(
        request: Request,
        metadataStorage: MetadataStorage,
        fileStorage: FileStorage
    ): Response {
        request.query("id")?.let { return Response(SEE_OTHER).header("Location", "$play/$it") }

        val uuid = request.path("id") ?: return Response(BAD_REQUEST)
        val metadata =
            metadataStorage.findTrack(UUID.fromString(uuid)).map { it }.orElse { null } ?: return Response(NOT_FOUND)
        val enhancedMetadata =
            when (val enhanced = with(AudioTrackMetadataEnhancer) { metadata.enhanceWithTakeNumber(metadataStorage) }) {
                is Success -> enhanced.value
                is Failure -> return Response(INTERNAL_SERVER_ERROR)
            }

        return fileStorage.stream(metadata.passwordProtectedLink).map { audioStream ->
            val dateTimePattern = DateTimeFormatter.ofPattern(
                DateTimePatterns.filenamePatternFor(metadata.recordedTimestampPrecision)
            )
            val dateTime = metadata.recordedTimestamp.format(dateTimePattern)
            val (title: String) = enhancedMetadata.base.preferredTitle()
            val headers: Headers = listOf(
                "Accept-Ranges" to "bytes",
                "Content-Length" to metadata.fileSize.toString(),
                "Content-Range" to "bytes 0-${metadata.fileSize - 1}/${metadata.fileSize}",
                "content-disposition" to "attachment; filename=${
                listOf(
                    dateTime,
                    "$title${enhancedMetadata.takeNumber?.let { " (take $it)" }.orEmpty()}"
                ).joinToString(" ")
                }.${metadata.format}"
            )

            // TODO stream should be closed.
            // TODO also - the downloader that provides the inputstream should also be closed.
            Response(OK).body(
                if (request.header("BANDAGE_DISABLE_EXPERIMENTAL_FEATURES") == "true") {
                    println("using original stream as BANDAGE_DISABLE_EXPERIMENTAL_FEATURES is true")
                    audioStream
                } else {
                    audioStream.newMp3Headers(metadata)
                }
            ).headers(headers)
        }.orElse {
            logger.warn(it.message)
            Response(NOT_FOUND)
        }
    }

    private fun InputStream.newMp3Headers(metadata: AudioTrackMetadata): InputStream {
        val tempDir = File("/tmp/bandage")
        if (! tempDir.exists()) tempDir.mkdir()
        val fifoPath = "${tempDir.absolutePath}/${metadata.uuid}"
        val mkFifo = listOf("mkfifo", fifoPath)
        ProcessBuilder().command(mkFifo).start().waitFor()
        val fifoFile = File(fifoPath)

        writeStreamToFile(fifoFile, metadata)

        val ffmpegNewMetadata = listOf(
            ffmpegForCurrentOs(),
            "-i", fifoFile.absolutePath,
            "-metadata", "artist=${metadata.artist}",
            "-metadata", "title=${metadata.preferredTitle().first}",
            "-acodec", "copy",
            "-f", metadata.format, "-"
        )

        val process = ProcessBuilder().command(ffmpegNewMetadata).start()

        return process.inputStream
    }

    private fun InputStream.writeStreamToFile(fifoFile: File, metadata: AudioTrackMetadata) {
        val ogThead = Thread.currentThread()
        val backgroundThread: Thread = thread(start = true, name = "write-${metadata.hash}") {
            this.use { input ->
                FileOutputStream(fifoFile).use { output ->
                    input.copyTo(output)
                }
            }
            ogThead.run { println("inputstream and outputstreams are both closed") } // TODO temp logging
        }

        // TODO temp logging
        var tripswitch = 1
        val copyingFile = Runnable {
            if (backgroundThread.isAlive) println("${backgroundThread.name} - copying file")
            else if (tripswitch == 1) {
                println("${backgroundThread.name} - copying complete.")
                tripswitch = 0
            }
        }
        val executor = Executors.newScheduledThreadPool(1)
        executor.scheduleAtFixedRate(copyingFile, 0, 1, TimeUnit.SECONDS)
    }

    private fun ffmpegForCurrentOs(): String =
        if (System.getProperty("os.name").toLowerCase().startsWith("mac")) {
            "lib/ffmpeg_darwin"
        } else {
            "lib/ffmpeg_linux_x64"
        }
}
