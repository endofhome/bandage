package functional.tests

import Authentication
import Bandage
import RouteMappings.play
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import config.BandageConfigItem.API_KEY
import config.dummyConfiguration
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.junit.jupiter.api.Test
import storage.DummyFileStorage
import storage.StubFileStorage
import storage.StubMetadataStorage

class PlayAudioTest {

    private val config = dummyConfiguration()
    private val metadataStorage = StubMetadataStorage(mutableListOf(exampleAudioFileMetadata))
    private val fileStorage = StubFileStorage(mapOf(exampleAudioFileMetadata.passwordProtectedLink to "some test data"))

    @Test
    fun `cannot access audio stream if not logged in`() {
        val bandage = Bandage(config, metadataStorage, DummyFileStorage).app
        val response = bandage(Request(GET, play).query("id", exampleAudioFileMetadata.uuid.toString()))

        assertThat(response.status, equalTo(FORBIDDEN))
    }

    @Test
    fun `returns NOT FOUND when no ID query parameter is present`() {
        val bandage = Bandage(config, metadataStorage, fileStorage).app
        val response = bandage(Request(GET, play)
            .cookie(Cookie(Authentication.loginCookieName, "${config.get(API_KEY)}_${1}", path = "login")))

        assertThat(response.status, equalTo(NOT_FOUND))
    }

    @Test
    fun `returns NOT FOUND when authenticated but file is not present in metadata storage`() {
        val emptyMetadataStorage = StubMetadataStorage(mutableListOf())
        val bandage = Bandage(config, emptyMetadataStorage, fileStorage).app
        val response = bandage(Request(GET, play)
            .query("id", exampleAudioFileMetadata.uuid.toString())
            .cookie(Cookie(Authentication.loginCookieName, "${config.get(API_KEY)}_${1}", path = "login")))

        assertThat(response.status, equalTo(NOT_FOUND))
    }

    @Test
    fun `returns NOT FOUND when authenticated but file is not present in file storage`() {
        val emptyFileStorage = StubFileStorage(emptyMap())
        val bandage = Bandage(config, metadataStorage, emptyFileStorage).app
        val response = bandage(Request(GET, play)
            .query("id", exampleAudioFileMetadata.uuid.toString())
            .cookie(Cookie(Authentication.loginCookieName, "${config.get(API_KEY)}_${1}", path = "login")))

        assertThat(response.status, equalTo(NOT_FOUND))
    }

    @Test
    fun `can access audio stream if logged in`() {
        val metadataStorage = StubMetadataStorage(mutableListOf(exampleAudioFileMetadata))
        val fileStorage = StubFileStorage(mapOf(exampleAudioFileMetadata.passwordProtectedLink to "some test data"))
        val bandage = Bandage(config, metadataStorage, fileStorage).app
        val response = bandage(Request(GET, play)
            .query("id", exampleAudioFileMetadata.uuid.toString())
            .cookie(Cookie(Authentication.loginCookieName, "${config.get(API_KEY)}_${1}", path = "login")))

        assertThat(response.status, equalTo(OK))
        val streamedData = String(response.body.stream.readAllBytes())
        assertThat(streamedData, equalTo("some test data"))
    }
}
