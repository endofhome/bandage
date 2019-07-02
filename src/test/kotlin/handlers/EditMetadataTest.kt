package handlers

import AuthenticatedRequest
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import exampleUser
import org.http4k.core.Method.GET
import org.http4k.core.Request
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.UriTemplate
import org.http4k.core.body.form
import org.http4k.routing.RoutedRequest
import org.junit.jupiter.api.Test
import storage.DummyMetadataStorage
import java.util.UUID

class EditMetadataTest {
    @Test
    fun `no id path parameter returns 400 BAD REQUEST`() {
        val authenticatedRequest = AuthenticatedRequest(
            RoutedRequest(
                Request(GET, "/tracks/"),
                UriTemplate.from("/tracks/{id}")
            ),
            exampleUser
        )

        assertThat(EditMetadata(authenticatedRequest, DummyMetadataStorage()).status, equalTo(BAD_REQUEST))
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

        assertThat(EditMetadata(authenticatedRequest, DummyMetadataStorage()).status, equalTo(BAD_REQUEST))
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

        assertThat(EditMetadata(authenticatedRequest, DummyMetadataStorage()).status, equalTo(BAD_REQUEST))
    }
}