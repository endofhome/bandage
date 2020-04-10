import org.http4k.core.Uri
import storage.AudioTrackMetadata
import storage.BitRate
import storage.Duration
import storage.Waveform
import storage.Waveform.Companion.Bits
import storage.Waveform.Companion.Channels
import storage.Waveform.Companion.Data
import storage.Waveform.Companion.Length
import storage.Waveform.Companion.SampleRate
import storage.Waveform.Companion.SamplesPerPixel
import storage.Waveform.Companion.Version
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit
import java.util.UUID

val exampleWaveform = Waveform(
    Bits(8),
    Data(
        listOf(
            0.0,
            0.01,
            0.12,
            0.34,
            0.56,
            0.78,
            0.99,
            1.0
        )
    ),
    Length(15000),
    Version(2),
    Channels(2),
    SampleRate(44100),
    SamplesPerPixel(44100)
)

val exampleAudioTrackMetadata = AudioTrackMetadata(
    UUID.fromString("68ab4da2-7ace-4e62-9db0-430af0ba487f"),
    "some artist",
    "some album",
    "some title",
    listOf("first working title", "second working title"),
    "mp3",
    BitRate("320000"),
    Duration("21"),
    12345,
    1234,
    Instant.EPOCH.atZone(UTC).plusHours(12).toString(),
    Instant.EPOCH.atZone(UTC).plusHours(12),
    ChronoUnit.MINUTES,
    Instant.EPOCH.atZone(UTC).plusHours(12),
    Uri.of("https://www.passwordprotectedlink.com"),
    "/my_folder/my_file",
    "someamazinghashstring",
    emptyList(),
    exampleWaveform
)

val exampleUser = User("some-user-id", "Some User")
