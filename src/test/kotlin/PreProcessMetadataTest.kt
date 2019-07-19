import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import storage.BitRate
import storage.Duration
import java.io.File
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.SECONDS

internal class PreProcessMetadataTest {
    private val baseExpected = PreProcessedAudioTrackMetadata(
        artist = "Test Tone Generator",
        workingTitle = "440Hz Sine Wave",
        format = "mp3",
        bitRate = BitRate("48000"),
        duration = Duration("5.089000"),
        fileSize = 31046,
        recordedTimestamp = null,
        recordedTimestampPrecision = null,
        hash = "d16670c5d6d17eeacba658150e0f853b2ba29e14783efb1e4f9692984db564ce"
    )

    @Test
    fun `can pre-process mp3 file without date or timestamp in filename`() {
        val testFile = File("src/test/resources/files/440Hz-5sec.mp3")

        val actual = PreProcessMetadata(testFile)

        assertThat(actual, equalTo(baseExpected))
    }

    @Nested
    @DisplayName("can pre-process mp3 file with only date in filename")
    inner class AuthenticatingUserAtLogin {
        private val baseTestFileDir = "src/test/resources/files/"
        private val baseTestFile = File("${baseTestFileDir}440Hz-5sec.mp3")

        @Test
        fun `with pattern yyyy-MM-dd`() {
            val testFileName = "${baseTestFileDir}2019-07-17 - Some recording.mp3"
            val tempFile = baseTestFile.copyTo(File(testFileName), overwrite = true)
            testFile(
                file = tempFile,
                expectedTimestamp = ZonedDateTime.of(2019, 7, 17, 0, 0, 0, 0, UTC),
                expectedPrecision = DAYS
            )
        }

        @Test
        fun `with pattern yyyy-MM-dd_hh-mm-ss`() {
            val testFileName = "${baseTestFileDir}2016-11-26_17-59-24.mp3"
            val tempFile = baseTestFile.copyTo(File(testFileName), overwrite = true)
            testFile(
                file = tempFile,
                expectedTimestamp = ZonedDateTime.of(2016, 11, 26, 17, 59, 24, 0, UTC),
                expectedPrecision = SECONDS
            )
        }
    }

    private fun testFile(
        file: File,
        expectedTimestamp: ZonedDateTime?,
        expectedPrecision: ChronoUnit
    ) {
        try {
            val expected = baseExpected.copy(
                recordedTimestamp = expectedTimestamp,
                recordedTimestampPrecision = expectedPrecision
            )
            val actual = PreProcessMetadata(file)

            assertThat(actual, equalTo(expected))
        } finally {
            file.delete()
        }
    }
}
