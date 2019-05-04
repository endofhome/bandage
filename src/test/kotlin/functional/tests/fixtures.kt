package functional.tests

import storage.AudioFileMetadata
import storage.BitRate
import storage.Duration
import java.util.UUID

val exampleAudioFileMetadata = AudioFileMetadata(
    UUID.fromString("68ab4da2-7ace-4e62-9db0-430af0ba487f"),
    "some artist",
    "some album",
    "some title",
    "mp3",
    BitRate("320000"),
    Duration("21"),
    12345,
    "10000",
    "https://www.passwordprotectedlink.com",
    "/my_folder/my_file",
    "someamazinghashstring"
)