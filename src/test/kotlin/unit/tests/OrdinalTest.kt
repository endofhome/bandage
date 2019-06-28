package unit.tests

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import handlers.Dashboard.Ordinal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OrdinalTest {
    @Test
    fun `test ordinals are correctly added`() {
        with(Ordinal) {
            assertThat(1.dayOfMonthToOrdinal(), equalTo("1st"))
            assertThat(2.dayOfMonthToOrdinal(), equalTo("2nd"))
            assertThat(3.dayOfMonthToOrdinal(), equalTo("3rd"))
            assertThat(4.dayOfMonthToOrdinal(), equalTo("4th"))
            assertThat(5.dayOfMonthToOrdinal(), equalTo("5th"))
            assertThat(6.dayOfMonthToOrdinal(), equalTo("6th"))
            assertThat(7.dayOfMonthToOrdinal(), equalTo("7th"))
            assertThat(8.dayOfMonthToOrdinal(), equalTo("8th"))
            assertThat(9.dayOfMonthToOrdinal(), equalTo("9th"))
            assertThat(10.dayOfMonthToOrdinal(), equalTo("10th"))
            assertThat(11.dayOfMonthToOrdinal(), equalTo("11th"))
            assertThat(12.dayOfMonthToOrdinal(), equalTo("12th"))
            assertThat(13.dayOfMonthToOrdinal(), equalTo("13th"))
            assertThat(14.dayOfMonthToOrdinal(), equalTo("14th"))
            assertThat(15.dayOfMonthToOrdinal(), equalTo("15th"))
            assertThat(16.dayOfMonthToOrdinal(), equalTo("16th"))
            assertThat(17.dayOfMonthToOrdinal(), equalTo("17th"))
            assertThat(18.dayOfMonthToOrdinal(), equalTo("18th"))
            assertThat(19.dayOfMonthToOrdinal(), equalTo("19th"))
            assertThat(20.dayOfMonthToOrdinal(), equalTo("20th"))
            assertThat(21.dayOfMonthToOrdinal(), equalTo("21st"))
            assertThat(22.dayOfMonthToOrdinal(), equalTo("22nd"))
            assertThat(23.dayOfMonthToOrdinal(), equalTo("23rd"))
            assertThat(24.dayOfMonthToOrdinal(), equalTo("24th"))
            assertThat(25.dayOfMonthToOrdinal(), equalTo("25th"))
            assertThat(26.dayOfMonthToOrdinal(), equalTo("26th"))
            assertThat(27.dayOfMonthToOrdinal(), equalTo("27th"))
            assertThat(28.dayOfMonthToOrdinal(), equalTo("28th"))
            assertThat(29.dayOfMonthToOrdinal(), equalTo("29th"))
            assertThat(30.dayOfMonthToOrdinal(), equalTo("30th"))
            assertThat(31.dayOfMonthToOrdinal(), equalTo("31st"))
        }
    }

    @Test
    fun `day of month cannot be less than 1`() {
        with(Ordinal) {
            val exception = assertThrows<IllegalArgumentException> {
                     0.dayOfMonthToOrdinal()
                 }
            assertEquals("Day of month cannot be less than 0 or more than 31", exception.message)
        }
    }

    @Test
    fun `day of month cannot be less than 31`() {
        with(Ordinal) {
            val exception = assertThrows<IllegalArgumentException> {
                32.dayOfMonthToOrdinal()
            }
            assertEquals("Day of month cannot be less than 0 or more than 31", exception.message)
        }
    }
}
