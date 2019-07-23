package handlers

import AuthenticatedRequest
import User
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.http4k.core.Method
import org.http4k.core.MultipartFormBody
import org.http4k.core.Request
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.junit.jupiter.api.Test

internal class UploadPreviewTest {
    @Test
    fun `returns 400 BAD REQUEST when no multipart form body`() {
        val request = Request(Method.POST, "http://dont.care")
            .header("content-type", "multipart/form-data; boundary=some-boundary")

        val user = User("some-user-id", "some-full-name")

        val response = UploadPreview(AuthenticatedRequest(request, user))

        assertThat(response.status, equalTo(BAD_REQUEST))
    }

    @Test
    fun `returns 400 BAD REQUEST when no content-type header is provided`() {
        val multipartBody = MultipartFormBody()
        val request = Request(Method.POST, "http://dont.care")
            .body(multipartBody)
        val user = User("some-user-id", "some-full-name")

        val response = UploadPreview(AuthenticatedRequest(request, user))

        assertThat(response.status, equalTo(BAD_REQUEST))
    }
}
