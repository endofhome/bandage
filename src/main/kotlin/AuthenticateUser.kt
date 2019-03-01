import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.body.formAsMap
import org.http4k.core.cookie.Cookie
import org.http4k.core.cookie.cookie
import java.lang.RuntimeException

object AuthenticateUser {
    operator fun invoke(request: Request): Response =
        Response(Status.SEE_OTHER).header("Location", "/dashboard").withBandageCookieFor(request.authenticatedUser())

    private fun Request.authenticatedUser(): String = formAsMap()["user"]?.first() ?: throw RuntimeException("User not found")

    private fun Response.withBandageCookieFor(user: String): Response =
        cookie(
            Cookie(
                name = "bandage_login",
                value = "${System.getenv("BANDAGE_API_KEY")}_$user",
                maxAge = 100000L,
                expires = null,
                domain = null,
                path = "login",
                secure = false,
                httpOnly = true
            )
        )
}