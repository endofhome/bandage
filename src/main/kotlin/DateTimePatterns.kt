import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.HOURS
import java.time.temporal.ChronoUnit.MINUTES
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.SECONDS
import java.time.temporal.ChronoUnit.YEARS

object DateTimePatterns {
    fun shortPatternFor(precision: ChronoUnit): String =
        when(precision) {
            SECONDS     -> "dd/MM/yyyy   HH:mm"
            MINUTES     -> "dd/MM/yyyy   HH:mm"
            HOURS       -> "dd/MM/yyyy"
            DAYS        -> "dd/MM/yyyy"
            MONTHS      -> "MM/yyyy"
            YEARS       -> "yyyy"
            else        -> error(unsupportedPatternMessage(precision))
        }

    fun longPatternFor(precision: ChronoUnit): String =
        when(precision) {
            SECONDS     -> "d MMMM yyyy"
            MINUTES     -> "d MMMM yyyy"
            HOURS       -> "d MMMM yyyy"
            DAYS        -> "d MMMM yyyy"
            MONTHS      -> "MMMM yyyy"
            YEARS       -> "yyyy"
            else        -> error(unsupportedPatternMessage(precision))
        }

    private fun unsupportedPatternMessage(precision: ChronoUnit) =
        "$precision precision is not supported"
}