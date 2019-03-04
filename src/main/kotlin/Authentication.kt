import Result.Failure
import Result.Success
import RouteMappings.dashboard
import RouteMappings.login
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.formAsMap
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie

class Authentication(private val users: UserManagement = UserManagement()) {

    fun authenticateUser(request: Request): Response =
        request.authenticatedUser().map { user ->
            Response(Status.SEE_OTHER).header("Location", dashboard).withBandageCookieFor(user)
        }.orElse { error ->
            Response(Status.SEE_OTHER).header("Location", login).body(error.message)
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
            password.first() != System.getenv("BANDAGE_PASSWORD") -> Failure(Error("Incorrect password"))
            else                                                  -> user.firstOrFailure().flatMap { users.findUser(it) }
        }
    }

    private fun Response.withBandageCookieFor(user: User): Response =
        cookie(
            Cookie(
                name = "bandage_login",
                value = "${System.getenv("BANDAGE_API_KEY")}_${user.userId}",
                maxAge = 100000L,
                expires = null,
                domain = null,
                path = "login",
                secure = false,
                httpOnly = true
            )
        )

    private fun List<String?>.firstOrFailure() =
        first()?.let { Success(it) } ?: Failure(Error("User field was empty"))
}
