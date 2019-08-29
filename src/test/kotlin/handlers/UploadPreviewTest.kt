package handlers

import AuthenticatedRequest
import Bandage
import User
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.FormFile
import org.http4k.core.MemoryResponse
import org.http4k.core.Method
import org.http4k.core.MultipartFormBody
import org.http4k.core.Request
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.testing.ApprovalTest
import org.http4k.testing.Approver
import org.http4k.testing.assertApproved
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.File

@ExtendWith(ApprovalTest::class)
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

    @Test
    fun `returns 400 BAD REQUEST when multipart form body and correct content-type header are provided, but no file`() {
        val multipartBody = MultipartFormBody()
        val request = Request(Method.POST, "http://dont.care")
            .header("content-type", "multipart/form-data; boundary=${multipartBody.boundary}")
            .body(multipartBody)

        val user = User("some-user-id", "some-full-name")

        val response = UploadPreview(AuthenticatedRequest(request, user))

        assertThat(response.status, equalTo(BAD_REQUEST))
    }

    @Test
    fun `returns 200 OK when multipart form body, correct content-type header and file are all provided`(approver: Approver) {
        val mp3File = File("src/test/resources/files/440Hz-5sec.mp3")
        val multipartBody = MultipartFormBody().plus(
            "file" to FormFile(mp3File.path, ContentType.OCTET_STREAM, mp3File.inputStream())
        )
        val request = Request(Method.POST, "http://dont.care")
            .header("content-type", "multipart/form-data; boundary=${multipartBody.boundary}")
            .body(multipartBody)

        val user = User("some-user-id", "some-full-name")

        val response: MemoryResponse = UploadPreview(AuthenticatedRequest(request, user)) as MemoryResponse
        val redactedResponse = if (System.getenv("${Bandage.StaticConfig.appName.toUpperCase()}_ARTIST_OVERRIDE") != null) {
            val overrideEncoded = System.getenv("${Bandage.StaticConfig.appName.toUpperCase()}_ARTIST_OVERRIDE").map {
                val intValue = it.toInt()
                if (intValue in 33..47) {
                    "&#x${Integer.toHexString(intValue)};"
                } else {
                    "$it"
                }
            }.joinToString("")

            response.copy(body = Body(
                response.bodyString().replace(
                    overrideEncoded,
                    "*** REDACTED ${Bandage.StaticConfig.appName.toUpperCase()}_ARTIST_OVERRIDE ***"
                )
            ))
        } else response

        assertThat(response.status, equalTo(OK))
        approver.assertApproved(redactedResponse, OK)
    }
}
