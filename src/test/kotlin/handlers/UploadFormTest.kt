package handlers

import AuthenticatedRequest
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import exampleUser
import org.http4k.core.Method
import org.http4k.core.Request
import org.jsoup.Jsoup
import org.junit.jupiter.api.Test

internal class UploadFormTest {
    @Test
    fun `unsupported file types produce an error message`() {
        val request = AuthenticatedRequest(Request(Method.GET, "https://something-blah-blah.com?unsupported-file-type=nope"), exampleUser)
        val responseDocument = Jsoup.parse(UploadForm(request).bodyString())
        val errorMessage = responseDocument.getElementById("error-message")

        assertThat(errorMessage.text(), equalTo("nope files are currently unsupported."))
    }
}