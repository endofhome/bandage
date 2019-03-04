import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.lens.BiDiBodyLens
import org.http4k.template.ViewModel

object Login {
    operator fun invoke(view: BiDiBodyLens<ViewModel>) =
        Response(Status.OK).with(view of LoginPage)

    object LoginPage : ViewModel {
        override fun template() = "login-page"
    }
}
