import RouteMappings.dashboard
import RouteMappings.login
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.http4k.core.ContentType
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.core.body.form
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookies
import org.http4k.core.with
import org.http4k.lens.Header
import org.junit.jupiter.api.Test

class AuthenticationTest {

    private val authentication = Authentication()

    @Test
    fun `handles valid login`() {
        val userId = "1"
        val request = Request(Method.POST, login)
            .with(Header.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
            .form("user", userId)
            .form("password", System.getenv("BANDAGE_PASSWORD"))

        val response = authentication.authenticateUser(request)
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
        assertThat(response.header("Location"), equalTo(dashboard))
        assertThat(response.cookies(), equalTo(listOf(validCookie)))
    }

    @Test
    fun `login without user is invalid`() {
        val request = Request(Method.POST, login)
            .with(Header.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)

        val response = authentication.authenticateUser(request)

        assertThat(response.status, equalTo(SEE_OTHER))
        assertThat(response.header("Location"), equalTo(login))
        assertThat(response.bodyString(), equalTo("User not provided"))
    }

    @Test
    fun `login without password is invalid`() {
        val request = Request(Method.POST, login)
            .with(Header.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
            .form("user", "1")

        val response = authentication.authenticateUser(request)

        assertThat(response.status, equalTo(SEE_OTHER))
        assertThat(response.header("Location"), equalTo(login))
        assertThat(response.bodyString(), equalTo("Password not provided"))
    }

    @Test
    fun `login with incorrect password is invalid`() {
        val request = Request(Method.POST, login)
            .with(Header.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
            .form("user", "3")
            .form("password", "wrong_password")

        val response = authentication.authenticateUser(request)

        assertThat(response.status, equalTo(SEE_OTHER))
        assertThat(response.header("Location"), equalTo(login))
        assertThat(response.bodyString(), equalTo("Incorrect password"))
    }

    @Test
    fun `ensure only one user field is provided`() {
        val request = Request(Method.POST, login)
            .with(Header.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
            .form("user", "1")
            .form("user", "2")
            .form("password", System.getenv("BANDAGE_PASSWORD"))

        val response = authentication.authenticateUser(request)

        assertThat(response.status, equalTo(SEE_OTHER))
        assertThat(response.header("Location"), equalTo(login))
        assertThat(response.bodyString(), equalTo("Multiple user fields are not allowed"))
    }

    @Test
    fun `user field cannot be empty`() {
        val request = Request(Method.POST, login)
            .with(Header.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
            .form("user", "")
            .form("password", System.getenv("BANDAGE_PASSWORD"))

        val response = authentication.authenticateUser(request)

        assertThat(response.status, equalTo(SEE_OTHER))
        assertThat(response.header("Location"), equalTo(login))
        assertThat(response.bodyString(), equalTo("User not provided"))
    }

    @Test
    fun `ensure only one password field is provided`() {
        val request = Request(Method.POST, login)
            .with(Header.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
            .form("user", "1")
            .form("password", System.getenv("BANDAGE_PASSWORD"))
            .form("password", "hunter2")

        val response = authentication.authenticateUser(request)

        assertThat(response.status, equalTo(SEE_OTHER))
        assertThat(response.header("Location"), equalTo(login))
        assertThat(response.bodyString(), equalTo("Multiple password fields are not allowed"))
    }

    @Test
    fun `invalid user ID cannot be logged in`() {
        val request = Request(Method.POST, login)
            .with(Header.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
            .form("user", "1")
            .form("password", System.getenv("BANDAGE_PASSWORD"))

        val noUsers = UserManagement(emptyList())
        val response = Authentication(noUsers).authenticateUser(request)

        assertThat(response.status, equalTo(SEE_OTHER))
        assertThat(response.header("Location"), equalTo(login))
        assertThat(response.bodyString(), equalTo("Unknown user ID 1"))
    }
}
