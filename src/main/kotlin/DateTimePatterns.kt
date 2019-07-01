import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.HOURS
import java.time.temporal.ChronoUnit.MINUTES
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.SECONDS
import java.time.temporal.ChronoUnit.YEARS

object DateTimePatterns {
    fun patternFor(precision: ChronoUnit) =
        when(precision) {
            SECONDS     -> "dd/MM/yyyy   HH:mm"
            MINUTES     -> "dd/MM/yyyy   HH:mm"
            HOURS       -> "dd/MM/yyyy"
            DAYS        -> "dd/MM/yyyy"
            MONTHS      -> "MM/yyyy"
            YEARS       -> "yyyy"
            else        -> error("$precision precision is not supported")
        }
}