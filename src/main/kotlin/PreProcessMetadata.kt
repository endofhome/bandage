import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import storage.BitRate
import storage.Duration
import storage.toBitRate
import storage.toDuration
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoUnit.SECONDS

object PreProcessMetadata {
    operator fun invoke(file: File): PreProcessedAudioTrackMetadata {
        val reader = metadataReader(file.path.escapeSpaces())
        val fileInfoJsonString = reader.readLines().joinToString("")
        val ffprobeInfo: FfprobeInfo = jacksonObjectMapper().readValue(fileInfoJsonString)
        val (timestamp, precision) = extractTimestamp(file)

        return PreProcessedAudioTrackMetadata(
            artist = ffprobeInfo.format.tags?.artist,
            workingTitle = ffprobeInfo.format.tags?.title,
            format = ffprobeInfo.format.format_name,
            bitRate = ffprobeInfo.streams.firstOrNull()?.bit_rate?.toBitRate(),
            duration = ffprobeInfo.format.duration?.toDuration(),
            fileSize = ffprobeInfo.format.size.toInt(),
            recordedTimestamp = timestamp,
            recordedTimestampPrecision = precision,
            hash = hashFile(file.readBytes())
        )
    }

    fun metadataReader(filePath: String): BufferedReader {
        val ffprobeMetadata = "lib/${ffprobeForCurrentOs()} -v quiet -print_format json -show_format -show_streams".split(" ").plus(filePath)
        val process = ProcessBuilder().command(ffprobeMetadata).start()
        return BufferedReader(InputStreamReader(process.inputStream))
    }

    private fun ffprobeForCurrentOs(): String =
        if (System.getProperty("os.name").toLowerCase().startsWith("mac")) {
            "ffprobe_darwin"
        } else {
            "ffprobe_linux_x64"
        }

    fun hashFile(file: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashAsBytes = digest.digest(file)

        return hashAsBytes.map { byte ->
            String.format("%02x", byte)
        }.joinToString("")
    }

    private fun String.escapeSpaces() = replace(" ", "\\ ")

    private fun extractTimestamp(file: File): Pair<ZonedDateTime, ChronoUnit> {
        val ctime = Files.getAttribute(Paths.get(file.path), "unix:ctime") as FileTime
        val timestamp = ZonedDateTime.ofInstant(ctime.toInstant(), UTC).removeNanos()
        return timestamp to SECONDS
    }

    private fun ZonedDateTime.removeNanos(): ZonedDateTime =
        run {
            this.minusNanos(nano.toLong())
        }
}

data class PreProcessedAudioTrackMetadata(
    val artist: String?,
    val workingTitle: String?,
    val format: String,
    val bitRate: BitRate?,
    val duration: Duration?,
    val fileSize: Int,
    val recordedTimestamp: ZonedDateTime,
    val recordedTimestampPrecision: ChronoUnit,
    val hash: String
)

data class FfprobeInfo(
    val streams: List<Stream>,
    val format: Format
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Stream(
    val bit_rate: String
)

data class Format(
    val filename: String,
    val nb_streams: Int,
    val nb_programs: Int,
    val format_long_name: String,
    val start_time: String?,
    val probe_score: Int,
    val format_name: String,
    val duration: String?,
    val size: String,
    val bit_rate: String,
    val tags: Tags?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Tags(
    val genre: String?,
    val comment: String?,
    val artist: String?,
    val album: String?,
    val title: String?,
    val date: String?
)
