package handlers

import AuthenticatedRequest
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import exampleAudioTrackMetadata
import exampleUser
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.UriTemplate
import org.http4k.core.body.form
import org.http4k.routing.RoutedRequest
import org.junit.jupiter.api.Test
import result.Error
import result.Result
import result.Result.Failure
import storage.AudioTrackMetadata
import storage.DummyMetadataStorage
import storage.StubMetadataStorage
import java.util.UUID

class EditTrackMetadataTests {
    @Test
    fun `no id path parameter returns 400 BAD REQUEST`() {
        val authenticatedRequest = AuthenticatedRequest(
            RoutedRequest(
                Request(GET, "/tracks/"),
                UriTemplate.from("/tracks/{id}")
            ),
            exampleUser
        )

        assertThat(EditTrackMetadata(authenticatedRequest, DummyMetadataStorage()).status, equalTo(BAD_REQUEST))
    }

    @Test
    fun `malformed uuid in path parameter returns 400 BAD REQUEST`() {
        val authenticatedRequest = AuthenticatedRequest(
            RoutedRequest(
                Request(GET, "/tracks/some_id"),
                UriTemplate.from("/tracks/{id}")
            ),
            exampleUser
        )

        assertThat(EditTrackMetadata(authenticatedRequest, DummyMetadataStorage()).status, equalTo(BAD_REQUEST))
    }

    @Test
    fun `missing title field in form returns 400 BAD REQUEST`() {
        val authenticatedRequest = AuthenticatedRequest(
            RoutedRequest(
                Request(GET, "/tracks/${UUID.randomUUID()}").form(
                    "not_title", "some value"
                ),
                UriTemplate.from("/tracks/{id}")
            ),
            exampleUser
        )

        assertThat(EditTrackMetadata(authenticatedRequest, DummyMetadataStorage()).status, equalTo(BAD_REQUEST))
    }

    @Test
    fun `uuid does not exist in metadata storage returns 404 NOT FOUND`() {
        val authenticatedRequest = AuthenticatedRequest(
            RoutedRequest(
                Request(GET, "/tracks/${UUID.nameUUIDFromBytes("this track does not exist".toByteArray())}")
                    .form("title", "some value")
                    .form("working_title", "some value"),
                UriTemplate.from("/tracks/{id}")
            ),
            exampleUser
        )

        val existingTrack = exampleAudioTrackMetadata.copy(uuid = UUID.nameUUIDFromBytes("this track exists".toByteArray()))
        val metadataStorage = StubMetadataStorage(mutableListOf(existingTrack))

        assertThat(EditTrackMetadata(authenticatedRequest, metadataStorage).status, equalTo(NOT_FOUND))
    }

    @Test
    fun `update to metadata failed returns 500 INTERNAL SERVER ERROR`() {
        val authenticatedRequest = AuthenticatedRequest(
            RoutedRequest(
                Request(GET, "/tracks/${exampleAudioTrackMetadata.uuid}")
                    .form("title", "some value")
                    .form("working_title", "some value"),
                UriTemplate.from("/tracks/{id}")
            ),
            exampleUser
        )

        val metadataStorage = object : StubMetadataStorage(mutableListOf(exampleAudioTrackMetadata)) {
            override fun updateTrack(updatedMetadata: AudioTrackMetadata): Result<Error, AudioTrackMetadata> =
                Failure(Error("metadata could not be updated"))
        }

        assertThat(EditTrackMetadata(authenticatedRequest, metadataStorage).status, equalTo(INTERNAL_SERVER_ERROR))
    }
}