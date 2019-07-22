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
        val timestampExtractors = listOf(
            TimestampExtractor5,
            TimestampExtractor4,
            TimestampExtractor3,
            TimestampExtractor2,
            TimestampExtractor1
        )

        return timestampExtractors.mapNotNull {
            when (val extracted = it.tryToExtract(this.path)) {
                null -> null
                else -> extracted
            }
        }.getOrElse(0) { null to null }
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

object TimestampExtractor1 : TimestampExtractor {
    private val regex = Regex("\\d{4}-\\d{2}-\\d{2}")

    override fun tryToExtract(fromPath: String): Pair<ZonedDateTime, ChronoUnit>? =
        regex.find(fromPath)?.let {
            val (year, month, day) = it.value.split("-").map(String::toInt)
            return ZonedDateTime.of(year, month, day, 0, 0, 0, 0, UTC) to ChronoUnit.DAYS
        }
}

object TimestampExtractor2 : TimestampExtractor {
    private val regex = Regex("\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}")

    override fun tryToExtract(fromPath: String): Pair<ZonedDateTime, ChronoUnit>? =
        regex.find(fromPath)?.let {
            val values = it.value.replace("_", "-").split("-").map(String::toInt)
            val (year, month, day, hour, minute) = values
            ZonedDateTime.of(year, month, day, hour, minute, values[5], 0, UTC) to ChronoUnit.SECONDS
        }
}

object TimestampExtractor3 : TimestampExtractor {
    private val regex = Regex("\\d{6}-\\d{6}")

    override fun tryToExtract(fromPath: String): Pair<ZonedDateTime, ChronoUnit>? =
        regex.find(fromPath)?.let {
            fun splitIntoPairs(acc: List<String>, remaining: String): List<String> {
                if (remaining.isEmpty()) return acc
                return splitIntoPairs(acc + remaining.take(2), remaining.drop(2))
            }
            val (date, time) = it.value.split("-")
            val pairs = listOf(date, time).flatMap { dateOrTime -> splitIntoPairs(emptyList(), dateOrTime) }
            val (year, month, day, hour, minute) = pairs.mapIndexed { i, e ->
                if (i == 0) { ("20$e").toInt() }
                else { e.toInt() }
            }
            ZonedDateTime.of(year, month, day, hour, minute, pairs[5].toInt(), 0, UTC) to ChronoUnit.SECONDS
        }
}

object TimestampExtractor4 : TimestampExtractor {
    private val regex = Regex("\\d{2}-\\d{2}-\\d{4}")

    override fun tryToExtract(fromPath: String): Pair<ZonedDateTime, ChronoUnit>? =
        regex.find(fromPath)?.let {
            val (day, month, year) = it.value.split("-").map(String::toInt)
            ZonedDateTime.of(year, month, day, 0, 0, 0, 0, UTC) to ChronoUnit.DAYS
        }
}

object TimestampExtractor5 : TimestampExtractor {
    private val regex = Regex("\\d{8}")

    override fun tryToExtract(fromPath: String): Pair<ZonedDateTime, ChronoUnit>? =
        regex.find(fromPath)?.let {
            fun splitWithYearFirst(acc: List<String>, remaining: String): List<String> =
                when {
                    remaining.isEmpty() -> acc
                    acc.isEmpty() -> splitWithYearFirst(acc + remaining.take(4), remaining.drop(4))
                    else -> splitWithYearFirst(acc + remaining.take(2), remaining.drop(2))
                }

            fun splitWithDayFirst(acc: List<String>, remaining: String): List<String> =
                when {
                    remaining.isEmpty() -> acc
                    acc.size == 2 -> splitWithDayFirst(acc + remaining.take(4), remaining.drop(4))
                    else -> splitWithDayFirst(acc + remaining.take(2), remaining.drop(2))
                }

            return try {
                val (year, month, day) = splitWithYearFirst(emptyList(), it.value).map { it.toInt() }
                ZonedDateTime.of(year, month, day, 0, 0, 0, 0, UTC) to ChronoUnit.DAYS
            } catch (e: Exception) {
                val (day, month, year) = splitWithDayFirst(emptyList(), it.value).map { it.toInt() }
                ZonedDateTime.of(year, month, day, 0, 0, 0, 0, UTC) to ChronoUnit.DAYS
            }
        }
}

interface TimestampExtractor {
    fun tryToExtract(fromPath: String): Pair<ZonedDateTime, ChronoUnit>?
}
