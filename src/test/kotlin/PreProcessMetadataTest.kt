import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.junit.jupiter.api.Test
import storage.BitRate
import storage.Duration
import java.io.File
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

internal class PreProcessMetadataTest {
    @Test
    fun `can pre-process mp3 file without date or timestamp in filename`() {

        val testFile = File("src/test/resources/files/440Hz-5sec.mp3")

        val expected = PreProcessedAudioTrackMetadata(
            artist = "Test Tone Generator",
            workingTitle = "440Hz Sine Wave",
            format = "mp3",
            bitRate = BitRate("48000"),
            duration = Duration("5.089000"),
            fileSize = 31046,
            recordedTimestamp = ZonedDateTime.of(2019, 7, 17, 17, 51, 14, 0, UTC),
            recordedTimestampPrecision = ChronoUnit.SECONDS,
            hash = "d16670c5d6d17eeacba658150e0f853b2ba29e14783efb1e4f9692984db564ce"
        )
        val actual = PreProcessMetadata(testFile)

        assertThat(actual, equalTo(expected))
    }
}