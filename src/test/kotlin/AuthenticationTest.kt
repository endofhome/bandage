import Authentication.Companion.loginCookieName
import RouteMappings.dashboard
import RouteMappings.index
import RouteMappings.login
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import org.http4k.core.ContentType
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.core.Uri
import org.http4k.core.body.form
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.core.cookie.cookies
import org.http4k.core.with
import org.http4k.lens.Header
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant.EPOCH
import java.time.LocalDateTime
import java.time.ZoneId

class AuthenticationTest {
    private val userManagement = UserManagement()
    private val authentication = Authentication(userManagement)

    @Nested
    @DisplayName("Authenticating user at login")
    inner class AuthenticatingUserAtLogin {
        @Test
        fun `handles valid login`() {
            val userId = "1"
            val request = Request(Method.POST, login)
                .with(Header.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("user", userId)
                .form("password", System.getenv("BANDAGE_PASSWORD"))

            val response = authentication.authenticateUser(request)
            val validCookie = Cookie(
                name = loginCookieName,
                value = "${System.getenv("BANDAGE_API_KEY")}_$userId",
                maxAge = 94608000L,
                expires = null,
                domain = null,
                path = index,
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

    @Test
    fun `handles logout, removing login cookie`() {
        val invalidatedCookie = Cookie(
            name = loginCookieName,
            value = "",
            maxAge = 0,
            expires = LocalDateTime.ofInstant(EPOCH, ZoneId.of("GMT"))
        )

        val response = authentication.logout()

        assertThat(response.status, equalTo(SEE_OTHER))
        assertThat(response.header("Location"), equalTo(login))
        assertThat(response.cookies(), equalTo(listOf(invalidatedCookie)))
    }

    @Nested
    @DisplayName("Authenticating requests")
    inner class AuthenticatingRequests {
        private val unauthenticatedRequest = Request(Method.GET, Uri.of("www.someuri.com"))
        private val handlerWithAuthentication = { request: Request ->
            authentication.ifAuthenticated(request) { Response(OK) }
        }

        @Test
        fun `can ensure unauthenticated user is returned to login when handling requests`() {
            val response = handlerWithAuthentication(unauthenticatedRequest)

            assertThat(response.status, equalTo(SEE_OTHER))
            assertThat(response.header("Location"), equalTo(login))
            assertThat(response.cookies(), equalTo(emptyList()))
        }

        @Test
        fun `can ensure authenticated user has their request handled`() {
            val validCookie = cookieFor(userManagement.users.last())
            val authenticatedRequest = unauthenticatedRequest.cookie(validCookie)

            val response = handlerWithAuthentication(authenticatedRequest)

            assertThat(response.status, equalTo(OK))
        }
    }

    private fun cookieFor(user: User): Cookie =
        Cookie(
            name = loginCookieName,
            value = "${System.getenv("BANDAGE_API_KEY")}_${user.userId}",
            maxAge = Long.MAX_VALUE,
            expires = null,
            domain = null,
            path = "login",
            secure = false,
            httpOnly = true
        )
}
