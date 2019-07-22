import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

interface TimestampExtractor {
    fun tryToExtract(fromPath: String): Pair<ZonedDateTime, ChronoUnit>?
}

object TimestampExtractor1 : TimestampExtractor {
    private val regex = Regex("\\d{4}-\\d{2}-\\d{2}")

    override fun tryToExtract(fromPath: String): Pair<ZonedDateTime, ChronoUnit>? =
        regex.find(fromPath)?.let {
            val (year, month, day) = it.value.split("-").map(String::toInt)
            return ZonedDateTime.of(year, month, day, 0, 0, 0, 0, ZoneOffset.UTC) to ChronoUnit.DAYS
        }
}

object TimestampExtractor2 : TimestampExtractor {
    private val regex = Regex("\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}")

    override fun tryToExtract(fromPath: String): Pair<ZonedDateTime, ChronoUnit>? =
        regex.find(fromPath)?.let {
            val values = it.value.replace("_", "-").split("-").map(String::toInt)
            val (year, month, day, hour, minute) = values
            ZonedDateTime.of(year, month, day, hour, minute, values[5], 0, ZoneOffset.UTC) to ChronoUnit.SECONDS
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
            ZonedDateTime.of(year, month, day, hour, minute, pairs[5].toInt(), 0, ZoneOffset.UTC) to ChronoUnit.SECONDS
        }
}

object TimestampExtractor4 : TimestampExtractor {
    private val regex = Regex("\\d{2}-\\d{2}-\\d{4}")

    override fun tryToExtract(fromPath: String): Pair<ZonedDateTime, ChronoUnit>? =
        regex.find(fromPath)?.let {
            val (day, month, year) = it.value.split("-").map(String::toInt)
            ZonedDateTime.of(year, month, day, 0, 0, 0, 0, ZoneOffset.UTC) to ChronoUnit.DAYS
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
                ZonedDateTime.of(year, month, day, 0, 0, 0, 0, ZoneOffset.UTC) to ChronoUnit.DAYS
            } catch (e: Exception) {
                val (day, month, year) = splitWithDayFirst(emptyList(), it.value).map { it.toInt() }
                ZonedDateTime.of(year, month, day, 0, 0, 0, 0, ZoneOffset.UTC) to ChronoUnit.DAYS
            }
        }
}
