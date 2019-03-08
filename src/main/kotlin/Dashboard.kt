import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.http4k.lens.BiDiBodyLens
import org.http4k.template.ViewModel

class Dashboard(
    private val view: BiDiBodyLens<ViewModel>,
    private val authentication: Authentication
) {
    operator fun invoke(request: Request): Response =
        with(authentication) {
            request.ifAuthenticated {
                Response(OK).with(view of DashboardPage)
            }
        }

    object DashboardPage : ViewModel {
        override fun template() = "dashboard"
    }
}
