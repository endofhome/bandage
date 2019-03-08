import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.http4k.lens.BiDiBodyLens
import org.http4k.template.ViewModel

object Dashboard {
    operator fun invoke(view: BiDiBodyLens<ViewModel>): Response =
        Response(OK).with(view of DashboardPage)

    object DashboardPage : ViewModel {
        override fun template() = "dashboard"
    }
}
