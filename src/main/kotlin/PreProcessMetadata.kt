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
import java.security.MessageDigest
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

object PreProcessMetadata {
    operator fun invoke(file: File): PreProcessedAudioTrackMetadata {
        val reader = metadataReader(file.path)
        val fileInfoJsonString = reader.readLines().joinToString("")
        val ffprobeInfo: FfprobeInfo = jacksonObjectMapper().readValue(fileInfoJsonString)
        val (timestamp, precision) = file.extractTimestamp()

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

    private fun File.extractTimestamp(): Pair<ZonedDateTime?, ChronoUnit?> {
        val pattern1 = Regex("\\d{4}-\\d{2}-\\d{2}")
        val pattern2 = Regex("\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}")
        val pattern3 = Regex("\\d{6}-\\d{6}")
        val pattern4 = Regex("\\d{2}-\\d{2}-\\d{4}")
        val pattern5 = Regex("\\d{8}")

        return pattern5.find(path)?.let { matchResult ->
            fun splitWithYearFirst(acc: List<String>, remaining: String): List<String> {
                return when {
                    remaining.isEmpty() -> acc
                    acc.isEmpty()       -> splitWithYearFirst(acc + remaining.take(4), remaining.drop(4))
                    else                -> splitWithYearFirst(acc + remaining.take(2), remaining.drop(2))
                }
            }

            return try {
                val (year, month, day) = splitWithYearFirst(emptyList(), matchResult.value).map { it.toInt() }
                ZonedDateTime.of(year, month, day, 0, 0, 0, 0, UTC) to ChronoUnit.DAYS
            } catch (e: Exception) {
                error(e)
            }
        }
        ?: pattern4.find(path)?.let { matchResult ->
            val (day, month, year) = matchResult.value.split("-").map(String::toInt)
            ZonedDateTime.of(year, month, day, 0, 0, 0, 0, UTC) to ChronoUnit.DAYS
        }
        ?: pattern3.find(path)?.let { matchResult ->
            fun splitIntoPairs(acc: List<String>, remaining: String): List<String> {
                if (remaining.isEmpty()) return acc
                return splitIntoPairs(acc + remaining.take(2), remaining.drop(2))
            }
            val (date, time) = matchResult.value.split("-")
            val pairs = listOf(date, time).flatMap { dateOrTime -> splitIntoPairs(emptyList(), dateOrTime) }
            val (year, month, day, hour, minute) = pairs.mapIndexed { i, e ->
                if (i == 0) { ("20$e").toInt() }
                else { e.toInt() }
            }
            ZonedDateTime.of(year, month, day, hour, minute, pairs[5].toInt(), 0, UTC) to ChronoUnit.SECONDS
        }
        ?: pattern2.find(path)?.let { matchResult ->
            val values = matchResult.value.replace("_", "-").split("-").map(String::toInt)
            val (year, month, day, hour, minute) = values
            ZonedDateTime.of(year, month, day, hour, minute, values[5], 0, UTC) to ChronoUnit.SECONDS
        }
        ?: pattern1.find(path)?.let { matchResult ->
            val (year, month, day) = matchResult.value.split("-").map(String::toInt)
            ZonedDateTime.of(year, month, day, 0, 0, 0, 0, UTC) to ChronoUnit.DAYS
        }
        ?: null to null
    }

    fun hashFile(file: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashAsBytes = digest.digest(file)

        return hashAsBytes.map { byte ->
            String.format("%02x", byte)
        }.joinToString("")
    }
}

data class PreProcessedAudioTrackMetadata(
    val artist: String?,
    val workingTitle: String?,
    val format: String,
    val bitRate: BitRate?,
    val duration: Duration?,
    val fileSize: Int,
    val recordedTimestamp: ZonedDateTime?,
    val recordedTimestampPrecision: ChronoUnit?,
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
