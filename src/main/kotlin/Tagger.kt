import storage.AudioTrackMetadata
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object Tagger {
    fun InputStream.addId3v2Tags(metadata: AudioTrackMetadata): InputStream {
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
            if (backgroundThread.isAlive) println("${Instant.now().epochSecond}: ${backgroundThread.name} - copying file")
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