import Authentication.Companion.Cookies.LOGIN
import Authentication.Companion.Cookies.REDIRECT
import RouteMappings.dashboard
import RouteMappings.index
import RouteMappings.login
import config.BandageConfigItem.API_KEY
import config.BandageConfigItem.PASSWORD
import config.Configuration
import io.jsonwebtoken.JwtParser
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.body.formAsMap
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.core.cookie.invalidateCookie
import org.slf4j.LoggerFactory
import result.Error
import result.Result
import result.Result.Failure
import result.Result.Success
import result.asSuccess
import result.flatMap
import result.map
import result.orElse
import storage.toUri

class Authentication(private val config: Configuration, private val users: UserManagement) {

    companion object {
        enum class Cookies(val cookieName: String) {
            LOGIN("bandage_login"),
            REDIRECT("bandage_redirect")
        }
    }

    private val minimumBits = (1..4).joinToString("") { config.get(API_KEY) }
    private val secretKey = Keys.hmacShaKeyFor(minimumBits.toByteArray())
    internal val jwtParser: JwtParser = Jwts.parser().setSigningKey(secretKey)

    private val logger = LoggerFactory.getLogger(Authentication::class.java)

    fun authenticateUser(request: Request): Response =
        request.authenticatedUser().map { user ->
            val formAsMap: Map<String, List<String?>> = request.formAsMap()
            val redirectUri = formAsMap["redirect"]?.single()
            logger.info("User ${user.userId} was successfully logged in, redirecting to $redirectUri")
            Response(Status.SEE_OTHER).header("Location", redirectUri).withLoginCookieFor(user).withJwtFor(user)
        }.orElse { error ->
            logger.warn("Unsuccessful login attempt: ${error.message}")
            Response(Status.SEE_OTHER).header("Location", login).body(error.message)
        }

    fun logout(): Response =
        Response(Status.SEE_OTHER).header("Location", login).invalidateCookie(LOGIN.cookieName).invalidateCookie(REDIRECT.cookieName)

    fun ifAuthenticated(
        request: Request,
        then: (AuthenticatedRequest) -> Response,
        otherwise: Response = Response(Status.SEE_OTHER).header("Location", login).cookie(redirectCookie(request.uri.plusDashboardFragmentIdentifier()))
    ): Response =
        request.cookie(LOGIN.cookieName).isValid().flatMap { cookie ->
            cookie.authenticatedUser().map { user ->
                then(AuthenticatedRequest(request, user))
            }
        }.orElse { err ->
            logger.warn(err.message)
            request.authenticatedJwtUser().map { user ->
                then(AuthenticatedRequest(request, user))
            }.orElse {
                otherwise
            }
        }

    private fun Request.authenticatedJwtUser(): Result<Error, User> =
        try {
            header("Authorization")?.let { token ->
                val parsed = jwtParser.parseClaimsJws(token.substringAfter("Bearer "))
                val userId: String = parsed.body["userId"].toString()
                users.findUser(userId).map { user ->
                    user.asSuccess()
                }.orElse { err ->
                    error(err.message)
                }
            } ?: error("Authorization header was missing")
        } catch (e: Exception) {
            Failure(Error(e.message ?: "Error while authenticating via JWT"))
        }

    private fun Request.authenticatedUser(): Result<Error, User> {
        val formAsMap: Map<String, List<String?>> = formAsMap()
        val user = formAsMap["user"]
        val password = formAsMap["password"]
        return when {
            user == null || user.first() == ""       -> Failure(Error("User not provided"))
            password == null                         -> Failure(Error("Password not provided"))
            user.size > 1                            -> Failure(Error("Multiple user fields are not allowed"))
            password.size > 1                        -> Failure(Error("Multiple password fields are not allowed"))
            password.first() != config.get(PASSWORD) -> Failure(Error("Incorrect password"))
            else                                     -> user.firstOrFailure().flatMap { users.findUser(it) }
        }
    }

    private fun Response.withLoginCookieFor(user: User): Response = cookie(cookieFor(user))

    private fun Response.withJwtFor(user: User): Response =
        this.body(jwtFor(user))

    internal fun jwtFor(user: User): String = Jwts.builder().claim("userId", user.userId).signWith(secretKey).compact()

    private fun cookieFor(user: User): Cookie =
        Cookie(
            name = LOGIN.cookieName,
            value = "${config.get(API_KEY)}_${user.userId}",
            maxAge = 94608000L,
            expires = null,
            domain = null,
            path = index,
            secure = false,
            httpOnly = true
        )

    private fun redirectCookie(redirectUri: Uri): Cookie =
        Cookie(
            name = REDIRECT.cookieName,
            value = "$redirectUri",
            maxAge = 94608000L,
            expires = null,
            domain = null,
            path = index,
            secure = false,
            httpOnly = true
        )

    private fun Cookie?.isValid(): Result<Error, Cookie> {
        if (this == null) return Failure(Error("Cookie is not present"))

        val (apiKey) = this.value.split("_")

        return when (apiKey) {
            config.get(API_KEY) -> Success(this)
            else                -> Failure(Error("Cookie is invalid"))
        }
    }

    private fun Cookie.authenticatedUser(): Result<Error, User> {
        val (apiKey, user) = this.value.split("_")

        return when (apiKey) {
            config.get(API_KEY) -> users.findUser(user)
            else                -> Failure(Error("Cookie is invalid"))
        }
    }

    private fun List<String?>.firstOrFailure() =
        first()?.let { Success(it) } ?: Failure(Error("User field was empty"))
}

data class AuthenticatedRequest(val request: Request, val user: User)

private fun Uri.plusDashboardFragmentIdentifier(): Uri =
    if (this.path.startsWith(dashboard)) {
        val id = this.query.substringAfter("id=").substringBefore("&")
        "$this#$id".toUri()
    } else {
        this
    }
