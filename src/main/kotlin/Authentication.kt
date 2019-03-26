import RouteMappings.dashboard
import RouteMappings.index
import RouteMappings.login
import config.BandageConfigItem.API_KEY
import config.BandageConfigItem.PASSWORD
import config.Configuration
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.formAsMap
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import org.http4k.core.cookie.invalidateCookie
import org.slf4j.LoggerFactory
import result.Error
import result.Result
import result.Result.Failure
import result.Result.Success
import result.flatMap
import result.map
import result.orElse

class Authentication(private val config: Configuration, private val users: UserManagement) {

    companion object {
        const val loginCookieName = "bandage_login"
    }

    private val logger = LoggerFactory.getLogger(Authentication::class.java)

    fun authenticateUser(request: Request): Response =
        request.authenticatedUser().map { user ->
            logger.info("User ${user.userId} was successfully logged in")

            logger.trace("TEST: Hello World!");
            logger.debug("TEST: How are you today?");
            logger.info("TEST: I am fine.");
            logger.warn("TEST: I love programming.");
            logger.error("TEST: I am programming.");

            Response(Status.SEE_OTHER).header("Location", dashboard).withBandageCookieFor(user)
        }.orElse { error ->
            logger.info("Unsuccessful login attempt: ${error.message}")
            Response(Status.SEE_OTHER).header("Location", login).body(error.message)
        }

    fun logout(): Response =
        Response(Status.SEE_OTHER).header("Location", login).invalidateCookie(loginCookieName)

    fun ifAuthenticated(
        request: Request,
        then: (Request) -> Response,
        otherwise: Response = Response(Status.SEE_OTHER).header("Location", login).body("User not authenticated")
    ): Response =
        if (request.cookie(loginCookieName).isValid()) {
            then(request)
        } else {
            otherwise
        }

    private fun Request.authenticatedUser(): Result<Error, User> {
        val formAsMap: Map<String, List<String?>> = formAsMap()
        val user = formAsMap["user"]
        val password = formAsMap["password"]
        return when {
            user == null || user.first() == ""                    -> Failure(Error("User not provided"))
            password == null                                      -> Failure(Error("Password not provided"))
            user.size > 1                                         -> Failure(Error("Multiple user fields are not allowed"))
            password.size > 1                                     -> Failure(Error("Multiple password fields are not allowed"))
            password.first() != config.get(PASSWORD)              -> Failure(Error("Incorrect password"))
            else                                                  -> user.firstOrFailure().flatMap { users.findUser(it) }
        }
    }

    private fun Response.withBandageCookieFor(user: User): Response = cookie(cookieFor(user))

    private fun cookieFor(user: User): Cookie =
        Cookie(
            name = loginCookieName,
            value = "${config.get(API_KEY)}_${user.userId}",
            maxAge = 94608000L,
            expires = null,
            domain = null,
            path = index,
            secure = false,
            httpOnly = true
        )

    private fun Cookie?.isValid(): Boolean {
        if (this == null) return false

        val (apiKey, user) = this.value.split("_")
        return when {
            apiKey == config.get(API_KEY) && users.findUser(user) is Success -> true
            else                                                                          -> false
        }
    }

    private fun List<String?>.firstOrFailure() =
        first()?.let { Success(it) } ?: Failure(Error("User field was empty"))
}
