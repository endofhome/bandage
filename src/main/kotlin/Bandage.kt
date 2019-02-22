import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.server.Jetty
import org.http4k.server.asServer

fun main(args: Array<String>) {
   val port = if (args.isNotEmpty()) args[0].toInt() else 7000
   Bandage.asServer(Jetty(port)).start()

   println("Bandage has started on port $port")
}

object Bandage: (Request) -> Response {
   override fun invoke(request: Request): Response = Response(OK)
}
