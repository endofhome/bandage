package handlers

import Authentication.Companion.Cookies.REDIRECT
import Bandage.StaticConfig.view
import RouteMappings.index
import User
import UserManagement
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Uri
import org.http4k.core.cookie.cookie
import org.http4k.core.cookie.invalidateCookie
import org.http4k.core.with
import org.http4k.template.ViewModel

object Login {
    operator fun invoke(request: Request, userManagement: UserManagement): Response {
        val redirectUri = request.cookie(REDIRECT.cookieName)?.value ?: index

        return Response(Status.OK).with(view of LoginPage(userManagement.users, Uri.of(redirectUri))).invalidateCookie(REDIRECT.cookieName)
    }

    data class LoginPage(val users: List<User>, val redirectUri: Uri) : ViewModel {
        override fun template() = "login"
    }
}
