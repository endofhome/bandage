import java.io.File
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

interface TimestampExtractor {
    val regex: Regex

    fun tryToExtract(from: File): Triple<ZonedDateTime, ChronoUnit, String>?
    fun String.remainingCharacters() = this.replace(regex, "").trim()
}

object TimestampExtractor1 : TimestampExtractor {
    override val regex = Regex("\\d{4}-\\d{2}-\\d{2}")

    override fun tryToExtract(from: File): Triple<ZonedDateTime, ChronoUnit, String>? =
        regex.find(from.path)?.let {
            val (year, month, day) = it.value.split("-").map(String::toInt)
            return Triple(
                ZonedDateTime.of(year, month, day, 0, 0, 0, 0, ZoneOffset.UTC),
                ChronoUnit.DAYS,
                from.name.remainingCharacters()
            )
        }
}

object TimestampExtractor2 : TimestampExtractor {
    override val regex = Regex("\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}")

    override fun tryToExtract(from: File): Triple<ZonedDateTime, ChronoUnit, String>? =
        regex.find(from.path)?.let {
            val values = it.value.replace("_", "-").split("-").map(String::toInt)
            val (year, month, day, hour, minute) = values
            Triple(
                ZonedDateTime.of(year, month, day, hour, minute, values[5], 0, ZoneOffset.UTC),
                ChronoUnit.SECONDS,
                from.name.remainingCharacters()
            )
        }
}

object TimestampExtractor3 : TimestampExtractor {
    override val regex = Regex("\\d{6}-\\d{6}")

    override fun tryToExtract(from: File): Triple<ZonedDateTime, ChronoUnit, String>? =
        regex.find(from.path)?.let {
            fun splitIntoTriples(acc: List<String>, remaining: String): List<String> {
                if (remaining.isEmpty()) return acc
                return splitIntoTriples(acc + remaining.take(2), remaining.drop(2))
            }
            val (date, time) = it.value.split("-")
            val pairs = listOf(date, time).flatMap { dateOrTime -> splitIntoTriples(emptyList(), dateOrTime) }
            val (year, month, day, hour, minute) = pairs.mapIndexed { i, e ->
                if (i == 0) { ("20$e").toInt() }
                else { e.toInt() }
            }
            Triple(
                ZonedDateTime.of(year, month, day, hour, minute, pairs[5].toInt(), 0, ZoneOffset.UTC),
                ChronoUnit.SECONDS,
                from.name.remainingCharacters()
            )
        }
}

object TimestampExtractor4 : TimestampExtractor {
    override val regex = Regex("\\d{2}-\\d{2}-\\d{4}")

    override fun tryToExtract(from: File): Triple<ZonedDateTime, ChronoUnit, String>? =
        regex.find(from.path)?.let {
            val (day, month, year) = it.value.split("-").map(String::toInt)
            Triple(
                ZonedDateTime.of(year, month, day, 0, 0, 0, 0, ZoneOffset.UTC),
                ChronoUnit.DAYS,
                from.name.remainingCharacters()
            )
        }
}

object TimestampExtractor5 : TimestampExtractor {
    override val regex = Regex("\\d{8}")

    override fun tryToExtract(from: File): Triple<ZonedDateTime, ChronoUnit, String>? =
        regex.find(from.path)?.let {
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
                Triple(
                    ZonedDateTime.of(year, month, day, 0, 0, 0, 0, ZoneOffset.UTC),
                    ChronoUnit.DAYS,
                    from.name.remainingCharacters()
                )
            } catch (e: Exception) {
                val (day, month, year) = splitWithDayFirst(emptyList(), it.value).map { it.toInt() }
                Triple(
                    ZonedDateTime.of(year, month, day, 0, 0, 0, 0, ZoneOffset.UTC),
                    ChronoUnit.DAYS,
                    from.name.remainingCharacters()
                )
            }
        }
}
