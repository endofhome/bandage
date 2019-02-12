import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK

object Bandage: (Request) -> Response {
   override fun invoke(request: Request): Response = Response(OK)
}
