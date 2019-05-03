package handlers

import Bandage.StaticConfig.view
import User
import UserManagement
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.template.ViewModel

object Login {
    operator fun invoke(userManagement: UserManagement) =
        Response(Status.OK).with(view of LoginPage(userManagement.users))

    data class LoginPage(val users: List<User>) : ViewModel {
        override fun template() = "login-page"
    }
}
