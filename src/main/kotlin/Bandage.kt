import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.with
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.bind
import org.http4k.routing.ResourceLoader
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.ViewModel
import org.http4k.template.view

fun main(args: Array<String>) {
    val port = if (args.isNotEmpty()) args[0].toInt() else Bandage.defaultPort
    Bandage.routes.asServer(Jetty(port)).start()

    println("Bandage has started on http://localhost:$port")
}

object Bandage {
    const val defaultPort = 7000
    private val renderer = HandlebarsTemplates().HotReload("src/main/resources")
    private val view = Body.view(renderer, ContentType.TEXT_HTML)

    val routes = routes(
            "/" bind GET to loginPage(),
            "/login" bind GET to loginPage(),
            "/login" bind POST to { request -> AuthenticateUser(request) },

            "/public" bind static(ResourceLoader.Directory("public"))
        )

    private fun loginPage(): (Request) -> Response =
        { Response(OK).with(view of LoginPage) }

    object LoginPage : ViewModel {
        override fun template() = "login-page"
    }
}
