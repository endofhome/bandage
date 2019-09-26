package functional.tests

import Authentication.Companion.Cookies.LOGIN
import Bandage
import RouteMappings.play
import Tagger
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import config.BandageConfigItem.API_KEY
import config.dummyConfiguration
import exampleAudioTrackMetadata
import org.http4k.core.Headers
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
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
import java.util.UUID
import PreProcessMetadata.hashFile as hashOf

class PlayAudioTest {

    private val config = dummyConfiguration()
    private val metadataStorage = StubMetadataStorage(mutableListOf(exampleAudioTrackMetadata))
    private val fileStorage = StubFileStorage(mutableMapOf(exampleAudioTrackMetadata.passwordProtectedLink to "some test data".toByteArray()))

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
        val take2 = exampleAudioTrackMetadata
        val take1 = take2.copy(uuid = UUID.nameUUIDFromBytes("take-1".toByteArray()), recordedTimestamp = exampleAudioTrackMetadata.recordedTimestamp.minusHours(1))
        val take3 = take2.copy(uuid = UUID.nameUUIDFromBytes("take-3".toByteArray()), recordedTimestamp = exampleAudioTrackMetadata.recordedTimestamp.plusHours(1))
        val metadataStorage = StubMetadataStorage(mutableListOf(take1, take2, take3))
        val fileContents = File("src/test/resources/files/440Hz-5sec.mp3").readBytes()
        val fileStorage = StubFileStorage(
            mutableMapOf(exampleAudioTrackMetadata.passwordProtectedLink to fileContents)
        )
        val bandage = Bandage(config, metadataStorage, fileStorage).app
        val response = bandage(Request(GET, "$play/${exampleAudioTrackMetadata.uuid}")
            .cookie(Cookie(LOGIN.cookieName, "${config.get(API_KEY)}_${1}", path = "login")))
        val expectedHeaders: Headers = listOf(
            "Accept-Ranges" to "bytes",
            "Content-Length" to take2.fileSize.toString(),
            "Content-Range" to "bytes 0-${take2.fileSize - 1}/${take2.fileSize}",
            "Content-Disposition" to "attachment; filename=1970-01-01 some title (take 2).${take2.format}"
        )

        assertThat(response.status, equalTo(OK))
        assertThat(response.headers, equalTo(expectedHeaders))
        val streamedData = response.body.stream.readAllBytes()
        val expectedData = with(Tagger) { fileContents.inputStream().addId3v2Tags(take2).readBytes() }
        assertThat(hashOf(streamedData), equalTo(hashOf(expectedData)))
    }

    // TODO test range requests
    // TODO should return partial content not OK

    @Test
    fun `redirects if logged in and ID query parameter is provided`() {
        val bandage = Bandage(config, DummyMetadataStorage(), DummyFileStorage()).app
        val response = bandage(Request(GET, play).query("id", exampleAudioTrackMetadata.uuid.toString())
            .cookie(Cookie(LOGIN.cookieName, "${config.get(API_KEY)}_${1}", path = "login")))

        assertThat(response.status, equalTo(SEE_OTHER))
        assertThat(response.header("Location"), equalTo("$play/${exampleAudioTrackMetadata.uuid}"))
    }
}
