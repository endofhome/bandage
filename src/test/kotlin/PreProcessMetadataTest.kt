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
    @DisplayName("can pre-process mp3 file with date/timestamp in filename")
    inner class PreProcessMp3WithTimestamps {
        private val baseTestFileDir = "src/test/resources/files/"
        private val baseTestFile = File("${baseTestFileDir}440Hz-5sec.mp3")

        @Test
        fun `with pattern yyyy-MM-dd`() {
            testFile(
                filename = "2019-07-17 - Some recording.mp3",
                expectedTimestamp = ZonedDateTime.of(2019, 7, 17, 0, 0, 0, 0, UTC),
                expectedPrecision = DAYS
            )
        }

        @Test
        fun `with pattern yyyy-MM-dd_hh-mm-ss`() {
            testFile(
                filename = "2016-11-26_17-59-24.mp3",
                expectedTimestamp = ZonedDateTime.of(2016, 11, 26, 17, 59, 24, 0, UTC),
                expectedPrecision = SECONDS
            )
        }

        @Test
        fun `with pattern yyMMdd-hhmmss`() {
            testFile(
                filename = "steal the mind logo - R_LINE_090301-200732.mp3",
                expectedTimestamp = ZonedDateTime.of(2009, 3, 1, 20, 7, 32, 0, UTC),
                expectedPrecision = SECONDS
            )
        }

        @Test
        fun `with pattern dd-MM-yyyy`() {
            testFile(
                filename = "IBG - Andrew bassline part 1 27-11-2005.mp3",
                expectedTimestamp = ZonedDateTime.of(2005, 11, 27, 0, 0, 0, 0, UTC),
                expectedPrecision = DAYS
            )
        }

        @Test
        fun `with pattern yyyyMMdd`() {
            testFile(
                filename = "IBG - Andrew bassline part 1 20051127.mp3",
                expectedTimestamp = ZonedDateTime.of(2005, 11, 27, 0, 0, 0, 0, UTC),
                expectedPrecision = DAYS
            )
        }

        @Test
        fun `with pattern ddMMyyyy`() {
            testFile(
                filename = "IBG - Andrew bassline part 1 27112005.mp3",
                expectedTimestamp = ZonedDateTime.of(2005, 11, 27, 0, 0, 0, 0, UTC),
                expectedPrecision = DAYS
            )
        }

        private fun testFile(
            filename: String,
            expectedTimestamp: ZonedDateTime,
            expectedPrecision: ChronoUnit
        ) {
            val file = baseTestFile.copyTo(File("$baseTestFileDir$filename"), overwrite = true)
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

    @Nested
    @DisplayName("unmatched characters in filename are returned to be re-used as the title, if there is no title tag present in file")
    inner class TrackTitleDerivingTests {
        @Test
        fun `filename with date`() {
            val file = File("src/test/resources/files/Some great tune 2019-07-24.mp3")
            val expected = baseExpected.copy(
                workingTitle = "Some great tune",
                recordedTimestamp = ZonedDateTime.of(2019, 7, 24, 0, 0, 0, 0, UTC),
                recordedTimestampPrecision = DAYS,
                hash = "e2715c74b5f018eae5db69a7dc1a94aaa6e71f10d1c6693c419a2d29c7ad899e"
            )
            val actual = PreProcessMetadata(file)

            assertThat(actual, equalTo(expected))
        }

        @Test
        fun `filename with no date or time`() {
            val file = File("src/test/resources/files/Some great tune.mp3")
            val expected = baseExpected.copy(
                workingTitle = "Some great tune",
                hash = "e2715c74b5f018eae5db69a7dc1a94aaa6e71f10d1c6693c419a2d29c7ad899e"
            )
            val actual = PreProcessMetadata(file)

            assertThat(actual, equalTo(expected))
        }
    }
}
