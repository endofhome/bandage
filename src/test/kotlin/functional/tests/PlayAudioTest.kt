package functional.tests

import Authentication.Companion.Cookies.LOGIN
import Bandage
import PreProcessMetadata
import RouteMappings.play
import Tagger.additionalBytesFor
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import config.BandageConfigItem.API_KEY
import config.BandageConfigItem.DISABLE_ID3_TAGGING_ON_THE_FLY
import config.dummyConfiguration
import exampleAudioTrackMetadata
import org.http4k.core.Headers
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.PARTIAL_CONTENT
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.core.Status.Companion.UNAUTHORIZED
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.junit.jupiter.api.Test
import storage.DummyFileStorage
import storage.DummyMetadataStorage
import storage.StubFileStorage
import storage.StubMetadataStorage
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import PreProcessMetadata.hashFile as hashOf

class PlayAudioTest {

    private val config = dummyConfiguration()
    private val metadataStorage = StubMetadataStorage(mutableListOf(exampleAudioTrackMetadata))
    private val fileStorage = StubFileStorage(
            mutableMapOf(
                exampleAudioTrackMetadata.path to "some test data".toByteArray()
            )
        )

    @Test
    fun `returns UNAUTHORISED if not logged in`() {
        val bandage = Bandage(config, DummyMetadataStorage(), DummyFileStorage()).app
        val response = bandage(Request(GET, "$play/${exampleAudioTrackMetadata.uuid}"))

        assertThat(response.status, equalTo(UNAUTHORIZED))
    }

    @Test
    fun `returns BAD REQUEST when no ID path parameter is present`() {
        val bandage = Bandage(config, DummyMetadataStorage(), DummyFileStorage()).app
        val response = bandage(Request(GET, play)
            .cookie(Cookie(LOGIN.cookieName, "${config.get(API_KEY)}_${1}", path = "login")))

        assertThat(response.status, equalTo(BAD_REQUEST))
    }

    @Test
    fun `returns NOT FOUND when authenticated but file is not present in metadata storage`() {
        val emptyMetadataStorage = StubMetadataStorage(mutableListOf())
        val bandage = Bandage(config, emptyMetadataStorage, fileStorage).app
        val response = bandage(Request(GET, "$play/${exampleAudioTrackMetadata.uuid}")
            .cookie(Cookie(LOGIN.cookieName, "${config.get(API_KEY)}_${1}", path = "login")))

        assertThat(response.status, equalTo(NOT_FOUND))
    }

    @Test
    fun `returns NOT FOUND when authenticated but file is not present in file storage`() {
        val emptyFileStorage = StubFileStorage(mutableMapOf())
        val bandage = Bandage(config, metadataStorage, emptyFileStorage).app
        val response = bandage(Request(GET, "$play/${exampleAudioTrackMetadata.uuid}")
            .cookie(Cookie(LOGIN.cookieName, "${config.get(API_KEY)}_${1}", path = "login")))

        assertThat(response.status, equalTo(NOT_FOUND))
    }

    @Test
    fun `can access audio stream if logged in`() {
        val file = File("src/test/resources/files/440Hz-5sec.mp3")
        val preProcessedMetadata = PreProcessMetadata(file)
        val take2 = exampleAudioTrackMetadata.copy(
            fileSize = file.length().toInt(),
            normalisedFileSize = preProcessedMetadata.normalisedFileSize
        )
        val take1 = take2.copy(
            uuid = UUID.nameUUIDFromBytes("take-1".toByteArray()),
            recordedTimestamp = take2.recordedTimestamp.minusHours(1)
        )
        val take3 = take2.copy(
            uuid = UUID.nameUUIDFromBytes("take-3".toByteArray()),
            recordedTimestamp = take2.recordedTimestamp.plusHours(1)
        )
        val metadataStorage = StubMetadataStorage(mutableListOf(take1, take2, take3))
        val fileStorage = StubFileStorage(
            mutableMapOf(exampleAudioTrackMetadata.path to file.readBytes())
        )
        val bandage = Bandage(config, metadataStorage, fileStorage).app
        val response = bandage(Request(GET, "$play/${exampleAudioTrackMetadata.uuid}")
            .cookie(Cookie(LOGIN.cookieName, "${config.get(API_KEY)}_${1}", path = "login")))
        val expectedFileSize = take2.normalisedFileSize?.plus(additionalBytesFor(
            mapOf(
                "artist" to take2.artist,
                "title"  to take2.title
            )
        )) ?: error("Could not compute expected file size for $take2")
        val expectedHeaders: Headers = listOf(
            "Accept-Ranges" to "bytes",
            "Content-Length" to expectedFileSize.toString(),
            "Content-Range" to "bytes 0-${expectedFileSize - 1}/$expectedFileSize",
            "Content-Type" to "audio/mpeg",
            "X-Content-Type-Options" to "nosniff",
            "Content-Disposition" to "attachment; filename=\"1970-01-01 some title (take 2).${take2.format}\""
        )

        assertThat(response.status, equalTo(OK))
        assertThat(response.headers, equalTo(expectedHeaders))

        val streamedData = response.body.stream.readAllBytes()
        assertThat(streamedData.size.toLong(), equalTo(expectedFileSize))
    }

    // TODO test range requests
    // TODO should return partial content not OK

    @Test
    fun `can access audio stream if id3 tagging is disabled in config`() {
        val take2 = exampleAudioTrackMetadata
        val take1 = take2.copy(uuid = UUID.nameUUIDFromBytes("take-1".toByteArray()), recordedTimestamp = exampleAudioTrackMetadata.recordedTimestamp.minusHours(1))
        val take3 = take2.copy(uuid = UUID.nameUUIDFromBytes("take-3".toByteArray()), recordedTimestamp = exampleAudioTrackMetadata.recordedTimestamp.plusHours(1))
        val metadataStorage = StubMetadataStorage(mutableListOf(take1, take2, take3))
        val fileContents = File("src/test/resources/files/440Hz-5sec.mp3").readBytes()
        val fileStorage = StubFileStorage(
            mutableMapOf(exampleAudioTrackMetadata.path to fileContents)
        )
        val configWithDisabledId3Tagging = config.withOverride(DISABLE_ID3_TAGGING_ON_THE_FLY, "true")

        val bandage = Bandage(configWithDisabledId3Tagging, metadataStorage, fileStorage).app
        val response = bandage(Request(GET, "$play/${exampleAudioTrackMetadata.uuid}")
            .cookie(Cookie(LOGIN.cookieName, "${config.get(API_KEY)}_${1}", path = "login")))
        val expectedHeaders: Headers = listOf(
            "Accept-Ranges" to "bytes",
            "Content-Length" to take2.fileSize.toString(),
            "Content-Range" to "bytes 0-${take2.fileSize - 1}/${take2.fileSize}",
            "Content-Type" to "audio/mpeg",
            "X-Content-Type-Options" to "nosniff",
            "Content-Disposition" to "attachment; filename=\"1970-01-01 some title (take 2).${take2.format}\""
        )

        assertThat(response.status, equalTo(OK))
        assertThat(response.headers, equalTo(expectedHeaders))
        val streamedData = response.body.stream.readAllBytes()
        val expectedData = fileContents.inputStream().readBytes()
        assertThat(hashOf(streamedData), equalTo(hashOf(expectedData)))
    }

    @Test
    fun `audio stream has new id3 tags if mp3 format - using experimental features`() {
        val metadataStorage = StubMetadataStorage(mutableListOf(exampleAudioTrackMetadata))
        val file = File("src/test/resources/files/440Hz-5sec.mp3")
        val fileContents = file.readBytes()
        val fileStorage = StubFileStorage(
            mutableMapOf(exampleAudioTrackMetadata.path to fileContents)
        )
        val bandage = Bandage(config, metadataStorage, fileStorage).app
        val response = bandage(Request(GET, "$play/${exampleAudioTrackMetadata.uuid}")
            .cookie(Cookie(LOGIN.cookieName, "${config.get(API_KEY)}_1", path = "login"))
            .header("BANDAGE_ENABLE_EXPERIMENTAL_FEATURES", "true"))

        val expectedMetadataBeforeStreaming = PreProcessMetadata(
            file = file,
            artistOverride = null
        )

        val fileContainingResponseBody = File("/tmp/audio_stream_has_new_id3_tags.mp3")
        response.body.stream.use { input ->
            FileOutputStream(fileContainingResponseBody).use { output ->
                input.copyTo(output)
            }
        }
        val expectedMetadataAfterStreaming = PreProcessMetadata(
            file = fileContainingResponseBody,
            artistOverride = "Sonic Potato"
        )

        assertThat(expectedMetadataBeforeStreaming.artist, equalTo("Test Tone Generator"))
        assertThat(expectedMetadataBeforeStreaming.workingTitle, equalTo("440Hz Sine Wave"))

        assertThat(expectedMetadataAfterStreaming.artist, equalTo("Sonic Potato"))
        assertThat(expectedMetadataAfterStreaming.workingTitle, equalTo("some title"))

        fileContainingResponseBody.delete()
    }

    @Test
    fun `audio stream is not manipulated if not mp3 format - using experimental features`() {
        val notAnMp3 = exampleAudioTrackMetadata.copy(format = "wav")
        val metadataStorage = StubMetadataStorage(mutableListOf(notAnMp3))
        val file = File("src/test/resources/files/not-an-mp3.wav")
        val fileContents = file.readBytes()
        val fileStorage = StubFileStorage(
            mutableMapOf(notAnMp3.path to fileContents)
        )
        val bandage = Bandage(config, metadataStorage, fileStorage).app
        val response = bandage(Request(GET, "$play/${notAnMp3.uuid}")
            .cookie(Cookie(LOGIN.cookieName, "${config.get(API_KEY)}_1", path = "login"))
            .header("BANDAGE_ENABLE_EXPERIMENTAL_FEATURES", "true"))

        val expectedMetadataBeforeStreaming = PreProcessMetadata(file)

        val fileContainingResponseBody = File("/tmp/not-an-mp3.wav")
        response.body.stream.use { input ->
            FileOutputStream(fileContainingResponseBody).use { output ->
                input.copyTo(output)
            }
        }

        val expectedMetadataAfterStreaming = PreProcessMetadata(fileContainingResponseBody)

        assertThat(fileContainingResponseBody.length(), equalTo(file.length()))
        assertThat(expectedMetadataAfterStreaming, equalTo(expectedMetadataBeforeStreaming))

        fileContainingResponseBody.delete()
    }

    @Test
    fun `id3 tags are added to mp3 files even when there are none or many - using experimental features`() {
        val noTags = File("src/test/resources/files/no-tags.mp3")
        val manyTags = File("src/test/resources/files/many-tags.mp3")
        listOf(noTags, manyTags).forEach { file ->
            val preProcessedMetadata = PreProcessMetadata(file)
            val track = exampleAudioTrackMetadata.copy(
                artist = "The Velvet Underground",
                title = "The Gift",
                fileSize = file.length().toInt(),
                normalisedFileSize = preProcessedMetadata.normalisedFileSize
            )

            val metadataStorage = StubMetadataStorage(mutableListOf(track))
            val fileStorage = StubFileStorage(
                mutableMapOf(exampleAudioTrackMetadata.path to file.readBytes())
            )
            val bandage = Bandage(config, metadataStorage, fileStorage).app
            val response = bandage(Request(GET, "$play/${exampleAudioTrackMetadata.uuid}")
                .cookie(Cookie(LOGIN.cookieName, "${config.get(API_KEY)}_1", path = "login"))
                .header("BANDAGE_ENABLE_EXPERIMENTAL_FEATURES", "true"))

            val fileContainingResponseBody = File("/tmp/id3_tags_are_added_even_when_there_are_none_or_many.mp3")
            response.body.stream.use { input ->
                FileOutputStream(fileContainingResponseBody).use { output ->
                    input.copyTo(output)
                }
            }

            val expectedMetadataAfterStreaming = PreProcessMetadata(
                file = fileContainingResponseBody
            )

            val expectedHeaders: Headers = listOf(
                "Accept-Ranges" to "bytes",
                "Content-Length" to "${expectedMetadataAfterStreaming.fileSize}",
                "Content-Range" to "bytes 0-${expectedMetadataAfterStreaming.fileSize - 1}/${expectedMetadataAfterStreaming.fileSize}",
                "Content-Type" to "audio/mpeg",
                "X-Content-Type-Options" to "nosniff",
                "Content-Disposition" to "attachment; filename=\"1970-01-01 The Gift.${track.format}\""
            )

            assertThat(response.status, equalTo(OK))
            assertThat(response.headers, equalTo(expectedHeaders))

            assertThat(fileContainingResponseBody.length(), equalTo(expectedMetadataAfterStreaming.fileSize.toLong()))
            assertThat(expectedMetadataAfterStreaming.artist, equalTo("The Velvet Underground"))
            assertThat(expectedMetadataAfterStreaming.workingTitle, equalTo("The Gift"))

            fileContainingResponseBody.delete()
        }
    }

    @Test
    fun `partial content is served if requested`() {
        val metadataStorage = StubMetadataStorage(mutableListOf(exampleAudioTrackMetadata))
        val file = File("src/test/resources/files/440Hz-5sec.mp3")
        val fileContents = file.readBytes()
        val fileStorage = StubFileStorage(
            mutableMapOf(exampleAudioTrackMetadata.path to fileContents)
        )
        val expectedNewTagBytes = 45
        val expectedNewFileSize = exampleAudioTrackMetadata.normalisedFileSize!! + expectedNewTagBytes

        val bandage = Bandage(config, metadataStorage, fileStorage).app
        val response = bandage(Request(GET, "$play/${exampleAudioTrackMetadata.uuid}")
            .cookie(Cookie(LOGIN.cookieName, "${config.get(API_KEY)}_1", path = "login"))
            .header("Range", "bytes=$expectedNewFileSize-"))

        assertThat(response.status, equalTo(PARTIAL_CONTENT))
        assertThat(response.header("Content-Length"), equalTo("1"))
    }

    @Test
    fun `redirects if logged in and ID query parameter is provided`() {
        val bandage = Bandage(config, DummyMetadataStorage(), DummyFileStorage()).app
        val response = bandage(Request(GET, play).query("id", exampleAudioTrackMetadata.uuid.toString())
            .cookie(Cookie(LOGIN.cookieName, "${config.get(API_KEY)}_${1}", path = "login")))

        assertThat(response.status, equalTo(SEE_OTHER))
        assertThat(response.header("Location"), equalTo("$play/${exampleAudioTrackMetadata.uuid}"))
    }
}
