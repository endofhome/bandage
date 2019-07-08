package unit.tests

import Authentication
import Authentication.Companion.Cookies
import Authentication.Companion.Cookies.LOGIN
import Authentication.RFC6749Body
import RouteMappings.api
import RouteMappings.dashboard
import RouteMappings.index
import RouteMappings.login
import User
import UserManagement
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import config.BandageConfigItem.API_KEY
import config.BandageConfigItem.PASSWORD
import config.dummyConfiguration
import org.http4k.core.ContentType
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.core.Status.Companion.UNAUTHORIZED
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
    private val config = dummyConfiguration()
    private val userManagement = UserManagement(config)
    private val authentication = Authentication(config, userManagement)
    private fun validCookie(userId: String) =
        Cookie(
            name = LOGIN.cookieName,
            value = "${config.get(API_KEY)}_$userId",
            maxAge = 94608000L,
            expires = null,
            domain = null,
            path = index,
            secure = false,
            httpOnly = true
        )

    @Nested
    @DisplayName("Authenticating user at login")
    inner class AuthenticatingUserAtLogin {
        @Test
        fun `handles valid login`() {
            val userId = "1"
            val request = Request(Method.POST, login)
                .with(Header.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("user", userId)
                .form("password", config.get(PASSWORD))
                .form("redirect", dashboard)

            val response = authentication.authenticateUser(request)

            assertThat(response.status, equalTo(SEE_OTHER))
            assertThat(response.header("Location"), equalTo(dashboard))
            assertThat(response.cookies(), equalTo(listOf(validCookie(userId))))
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
                .form("password", config.get(PASSWORD))

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
                .form("password", config.get(PASSWORD))

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
                .form("password", config.get(PASSWORD))
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
                .form("password", config.get(PASSWORD))

            val noUsers = UserManagement(config, emptyList())
            val response = Authentication(config, noUsers).authenticateUser(request)

            assertThat(response.status, equalTo(SEE_OTHER))
            assertThat(response.header("Location"), equalTo(login))
            assertThat(response.bodyString(), equalTo("Unknown user ID 1"))
        }
    }

    @Test
    fun `handles logout, removing login cookie`() {
        val invalidatedCookies = Cookies.values().map {
            Cookie(
                name = it.cookieName,
                value = "",
                maxAge = 0,
                expires = LocalDateTime.ofInstant(EPOCH, ZoneId.of("GMT"))
            )
        }

        val response = authentication.logout()

        assertThat(response.status, equalTo(SEE_OTHER))
        assertThat(response.header("Location"), equalTo(login))
        assertThat(response.cookies(), equalTo(invalidatedCookies))
    }

    @Nested
    @DisplayName("Authenticating user via API")
    inner class AuthenticatingUserViaApi {
        @Test
        fun `handles valid login`() {
            val userId = "1"
            val request = Request(Method.POST, "$api$login")
                .with(Header.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("user", userId)
                .form("password", config.get(PASSWORD))

            val response = authentication.authenticateUserApi(request)

            assertThat(response.status, equalTo(OK))
            assertThat(response.cookies(), equalTo(listOf(validCookie(userId))))

            val jwtString: RFC6749Body = jacksonObjectMapper().readValue(response.bodyString())
            val jwt = authentication.jwtParser.parseClaimsJws(jwtString.access_token)
            val userIdFromJwt = jwt.body["userId"].toString()
            assertThat(userIdFromJwt, equalTo(userId))
        }

        @Test
        fun `handles invalid login`() {
            val request = Request(Method.POST, "$api$login")
                .with(Header.CONTENT_TYPE of ContentType.APPLICATION_FORM_URLENCODED)
                .form("user", "1")
                .form("password", "password1")

            val response = authentication.authenticateUserApi(request)

            assertThat(response.status, equalTo(UNAUTHORIZED))
            assertThat(response.bodyString(), equalTo("Incorrect password"))
        }
    }

    @Nested
    @DisplayName("Authenticating requests")
    inner class AuthenticatingRequests {
        private val unauthenticatedRequest = Request(Method.GET, Uri.of("www.someuri.com"))
        private val handlerWithAuthentication = { request: Request ->
            authentication.ifAuthenticated(request, { (_, user) -> Response(OK).body(user.userId) })
        }
        private val cookieUser = userManagement.users.last()
        private val jwtUser = cookieUser.copy(userId = "2")

        @Test
        fun `can ensure unauthenticated user is returned to login when handling requests`() {
            val response = handlerWithAuthentication(unauthenticatedRequest)

            assertThat(response.status, equalTo(SEE_OTHER))
            assertThat(response.header("Location"), equalTo(login))
            assertThat(response.cookies().single().value, equalTo("www.someuri.com"))
        }

        @Test
        fun `can ensure authenticated user with only cookie has their request handled`() {
            val validCookie = cookieFor(cookieUser)
            val authenticatedRequest = unauthenticatedRequest.cookie(validCookie)

            val response = handlerWithAuthentication(authenticatedRequest)

            assertThat(response.status, equalTo(OK))
        }

        @Test
        fun `can ensure authenticated user with only JWT has their request handled`() {
            val validJwt = authentication.jwtFor(jwtUser)
            val authenticatedRequest = unauthenticatedRequest.header("Authorization", "Bearer $validJwt")

            val response = handlerWithAuthentication(authenticatedRequest)

            assertThat(response.status, equalTo(OK))
        }

        @Test
        fun `cookie takes precedence over JWT`() {
            val validCookie = cookieFor(cookieUser)
            val validJwt = authentication.jwtFor(jwtUser)
            val authenticatedRequest = unauthenticatedRequest
                .cookie(validCookie)
                .header("Authorization", "Bearer $validJwt")

            val response = handlerWithAuthentication(authenticatedRequest)

            assertThat(response.status, equalTo(OK))
            assertThat(response.bodyString(), equalTo(cookieUser.userId))
        }
    }

    private fun cookieFor(user: User): Cookie =
        Cookie(
            name = LOGIN.cookieName,
            value = "${config.get(API_KEY)}_${user.userId}",
            maxAge = Long.MAX_VALUE,
            expires = null,
            domain = null,
            path = "login",
            secure = false,
            httpOnly = true
        )
}
