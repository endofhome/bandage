package functional.tests

import Bandage
import RouteMappings.play
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import config.dummyConfiguration
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status.Companion.FORBIDDEN
import org.junit.jupiter.api.Test
import storage.DummyFileStorage
import storage.StubMetadataStorage

class PlayAudioTest {

    private val config = dummyConfiguration()

    @Test
    fun `cannot access audio stream if not logged in`() {
        val metadataStorage = StubMetadataStorage(mutableListOf(exampleAudioFileMetadata))
        val bandage = Bandage(config, metadataStorage, DummyFileStorage).app
        val response = bandage(Request(GET, play).query("id", exampleAudioFileMetadata.uuid.toString()))

        assertThat(response.status, equalTo(FORBIDDEN))
    }
}
