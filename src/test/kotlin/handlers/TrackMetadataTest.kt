package handlers

import AuthenticatedRequest
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import exampleUser
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.http4k.core.UriTemplate
import org.http4k.routing.RoutedRequest
import org.junit.jupiter.api.Test
import storage.DummyMetadataStorage

internal class TrackMetadataTest {
    @Test
    fun `malformed uuid in path parameter returns 400 BAD REQUEST`() {
        val authenticatedRequest = AuthenticatedRequest(
            RoutedRequest(
                Request(Method.GET, "/tracks/some_id"),
                UriTemplate.from("/tracks/{id}")
            ),
            exampleUser
        )

        assertThat(TrackMetadata(authenticatedRequest, DummyMetadataStorage()).status, equalTo(Status.BAD_REQUEST))
    }
}