import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.HOURS
import java.time.temporal.ChronoUnit.MINUTES
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.SECONDS
import java.time.temporal.ChronoUnit.YEARS

class DateTimePatternsTest {
    private val expectedResults = mapOf(
        SECONDS to "dd/MM/yyyy   HH:mm",
        MINUTES to "dd/MM/yyyy   HH:mm",
        HOURS to "dd/MM/yyyy",
        DAYS to "dd/MM/yyyy",
        MONTHS to "MM/yyyy",
        YEARS to "yyyy"
    )

    @Test
    fun `provides correct precision for given precision`() {
        expectedResults.entries.forEach {
            assertThat(DateTimePatterns.patternFor(it.key), equalTo(it.value))
        }
    }

    @Test
    fun `fails if given unknown precision`() {
        val unexpectedPrecisionValues = ChronoUnit.values().filter { ! expectedResults.keys.contains(it) }
        val exceptions = unexpectedPrecisionValues.map {
            assertThrows<IllegalStateException> {
                DateTimePatterns.patternFor(it)
            }
        }

        assertTrue(exceptions.all { it.message!!.contains("precision is not supported") })
    }
}