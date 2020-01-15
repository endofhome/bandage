import org.http4k.core.Uri
import storage.AudioTrackMetadata
import storage.BitRate
import storage.Duration
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit
import java.util.UUID

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
    "someamazinghashstring"
)

val exampleUser = User("some-user-id", "Some User")