import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.http4k.core.ContentType
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.core.Status.Companion.UNAUTHORIZED
import org.http4k.core.body.form
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookies
import org.http4k.core.with
import org.http4k.lens.Header
import org.junit.jupiter.api.Test

class AuthenticateUserTest {

    @Test
    fun `handles valid login`() {
        val userId = "1"
        val request = Request(Method.POST, "/login")
            .with(Header.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
            .form("user", userId)
            .form("password", "super-secret")

        val response = AuthenticateUser(request)
        val validCookie = Cookie(
            name = "bandage_login",
            value = "${System.getenv("BANDAGE_API_KEY")}_$userId",
            maxAge = 100000L,
            expires = null,
            domain = null,
            path = "login",
            secure = false,
            httpOnly = true
        )

        assertThat(response.status, equalTo(SEE_OTHER))
        assertThat(response.header("Location"), equalTo("/dashboard"))
        assertThat(response.cookies(), equalTo(listOf(validCookie)))
    }

    @Test
    fun `login without user is invalid`() {
        val request = Request(Method.POST, "/login")
            .with(Header.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)

        val response = AuthenticateUser(request)

        assertThat(response.status, equalTo(UNAUTHORIZED))
        assertThat(response.bodyString(), equalTo("User not provided"))
    }

    @Test
    fun `login without password is invalid`() {
        val request = Request(Method.POST, "/login")
            .with(Header.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
            .form("user", "2")

        val response = AuthenticateUser(request)

        assertThat(response.status, equalTo(UNAUTHORIZED))
        assertThat(response.bodyString(), equalTo("Password not provided"))
    }
}