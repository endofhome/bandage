import Tagger.Mode.AddId3Tags
import Tagger.Mode.Normalise
import storage.AudioTrackMetadata
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.random.Random

object Tagger {
    sealed class Mode {
        data class Normalise(val file: File) : Mode()
        data class AddId3Tags(val metadata: AudioTrackMetadata) : Mode()
    }

    fun InputStream.manipulate(mode: Mode): StreamWithLength {
        if (mode is AddId3Tags && mode.metadata.format != "mp3") return StreamWithLength(this, mode.metadata.fileSize.toLong())
        if (mode is Normalise && mode.file.extension != "mp3") return StreamWithLength(this, mode.file.length())
        val tempFileId: String = when (mode) {
            is AddId3Tags -> mode.metadata.uuid.toString()
            is Normalise  -> mode.file.path.replace("/", "__").replace(" ", "_")
        }

        val tempDir = File("/tmp/bandage")
        if (! tempDir.exists()) tempDir.mkdir()
        val startTime = Instant.now().toEpochMilli()
        val inputFifoPath = "${tempDir.absolutePath}/$tempFileId-$startTime-${Random.nextInt()}-input.mp3"
        val mkFifoInput = listOf("mkfifo", inputFifoPath)
        ProcessBuilder().command(mkFifoInput).start().waitFor()
        val inputFifoFile = File(inputFifoPath)

        writeStreamToFile(inputFifoFile)

        // TODO temp while migrating DB
        val newTags = when (mode) {
            is AddId3Tags -> mapOf(
                "artist" to mode.metadata.artist,
                "title" to mode.metadata.preferredTitle().first
            )
            is Normalise -> emptyMap()
        }

        val audioFormat = when (mode) {
            is AddId3Tags -> mode.metadata.format
            is Normalise  -> mode.file.extension
        }

        val ffmpegCommand = ffmpegCommand(audioFormat, newTags, inputFifoFile)

        println(ffmpegCommand.joinToString(" "))

        // TODO temp
        val tempLogFile = File("/tmp/ffmpeg-output-$startTime.txt")
        tempLogFile.createNewFile()

        val process = ProcessBuilder()
            // TODO temp
            .redirectError(tempLogFile)
            .command(ffmpegCommand)
            .start()

        val newTagBytes = additionalBytesFor(newTags)
        val newFileSize = when (mode) {
            is AddId3Tags -> mode.metadata.normalisedFileSize?.plus(newTagBytes) ?: mode.metadata.fileSize.toLong()
            is Normalise  -> process.inputStream.readAllBytes().size.toLong()
        }

        // TODO temp logging
        println(newTags)
        newTags.forEach {
            println(it.value + ": " + it.value.length)
            println(12 + it.value.length)
        }

        if (mode is AddId3Tags) {
            println("original file size: ${mode.metadata.fileSize}")
            println("normalised file size: ${mode.metadata.normalisedFileSize}")
            println("new file size: $newFileSize")
        }

        // TODO in the case of a normalised file, the inputstream is already used.
        // This is probably better as two separate functions
        // 1) adding id3 tags returns the inputstream and size.
        // 2) normalising just returns the size.
        return StreamWithLength(process.inputStream, newFileSize)
    }

    fun additionalBytesFor(newTags: Map<String, String>): Long =
        if (newTags.isEmpty()) 0L
        else newTags
                .map { tag -> 12L + tag.value.length }
                .reduce { acc, i -> acc + i }

    private fun ffmpegCommand(
        format: String,
        newTags: Map<String, String>,
        inputFile: File
    ): List<String> {

        val commandOpening = listOf(
            ffmpegForCurrentOs(),
            "-f", format,

            "-i", inputFile.absolutePath,
            "-y" // overwrite the file if it exists
        )
        val commandMetadataTags = newTags.flatMap { pair ->
            listOf(
                "-metadata", "${pair.key}=${pair.value}"
            )
        }
        val commandClosing = listOf(
            "-map", "0",
            "-map_metadata", "0:s:0",
            "-codec", "copy",
            "-f", format,
            "-"
        )

        return commandOpening + commandMetadataTags + commandClosing
    }

    private fun InputStream.writeStreamToFile(fifoFile: File) {
        val ogThead = Thread.currentThread()
        val backgroundThread: Thread = thread(start = true, name = "write-${fifoFile.path.replace(" ", "_").replace("/", "__")}") {
            this.use { input ->
                FileOutputStream(fifoFile).use { output ->
                    input.copyTo(output).also {
                        ogThead.run { println("bytes copied: $it") }
                    }

                }
            }
            ogThead.run { println("inputstream and outputstreams are both closed") } // TODO temp logging
        }

        // TODO temp logging
        var tripswitch = 1
        val copyingFile = Runnable {
            if (backgroundThread.isAlive) println("${Instant.now().epochSecond}: ${backgroundThread.name} - copying file") else
                if (!backgroundThread.isAlive && tripswitch == 1) {
                    println("${backgroundThread.name} - copying complete.")
                    tripswitch = 0
                }
        }
        val executor = Executors.newScheduledThreadPool(1)
        executor.scheduleAtFixedRate(copyingFile, 0, 1, TimeUnit.SECONDS)
    }

    private fun ffmpegForCurrentOs(): String =
        if (System.getProperty("os.name").toLowerCase().startsWith("mac")) {
            "lib/ffmpeg_darwin_4.2.2"
        } else {
            "lib/ffmpeg_linux_x64"
        }
}

data class StreamWithLength(val inputstream: InputStream, val size: Long)
