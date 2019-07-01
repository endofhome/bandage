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
    private data class Pattern(val shortPattern: String, val longPattern: String)

    private val expectedResults = mapOf(
        SECONDS to Pattern("dd/MM/yyyy   HH:mm", "d MMMM yyyy"),
        MINUTES to Pattern("dd/MM/yyyy   HH:mm", "d MMMM yyyy"),
        HOURS to Pattern("dd/MM/yyyy", "d MMMM yyyy"),
        DAYS to Pattern("dd/MM/yyyy", "d MMMM yyyy"),
        MONTHS to Pattern("MM/yyyy", "MMMM yyyy"),
        YEARS to Pattern("yyyy", "yyyy")
    )
    @Test
    fun `provides correct short pattern precision for given precision`() {
        expectedResults.entries.forEach {
            assertThat(DateTimePatterns.shortPatternFor(it.key), equalTo(it.value.shortPattern))
        }
    }

    @Test
    fun `provides correct long pattern precision for given precision`() {
        expectedResults.entries.forEach {
            assertThat(DateTimePatterns.longPatternFor(it.key), equalTo(it.value.longPattern))
        }
    }

    @Test
    fun `fails if given unknown precision`() {
        val unexpectedPrecisionValues = ChronoUnit.values().filter { ! expectedResults.keys.contains(it) }
        val exceptions = unexpectedPrecisionValues.map {
            assertThrows<IllegalStateException> {
                DateTimePatterns.shortPatternFor(it)
            }
        }

        assertTrue(exceptions.all { it.message!!.contains("precision is not supported") })
    }
}