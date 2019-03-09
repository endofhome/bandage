import Bandage.Config.defaultPort
import Bandage.Config.view
import RouteMappings.dashboard
import RouteMappings.index
import RouteMappings.login
import RouteMappings.logout
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.routing.ResourceLoader
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.view

fun main(args: Array<String>) {
    val port = if (args.isNotEmpty()) args[0].toInt() else defaultPort
    Bandage.routes.withFilter(EnforceHttpsOnHeroku()).asServer(Jetty(port)).start()

    println("Bandage has started on http://localhost:$port")
}

object Bandage {
    object Config {
        private val renderer = HandlebarsTemplates().HotReload("src/main/resources")
        val view = Body.view(renderer, ContentType.TEXT_HTML)
        const val defaultPort = 7000
    }

    private val userManagement = UserManagement()
    private val authentication = Authentication(userManagement)

    val routes = with(authentication) { routes(
            index       bind GET  to { Response(SEE_OTHER).header("Location", login) },
            login       bind GET  to { Login(view, userManagement) },
            login       bind POST to { request -> authenticateUser(request) },
            logout      bind GET  to { logout() },
            dashboard   bind GET  to { request -> ifAuthenticated(request) { Dashboard(view) } },

            "/public" bind static(ResourceLoader.Directory("public"))
        )
    }
}

object EnforceHttpsOnHeroku {
    operator fun invoke(): Filter = Filter { next -> { enforceHttps(next, it) } }

    private fun enforceHttps(handle: HttpHandler, request: Request): Response =
        if (insecureHttp(request) && probablyOnHeroku) {
            val herokuHost = "band-age.herokuapp.com"
            Response(SEE_OTHER).header("Location", request.uri.copy(scheme = "https", host = herokuHost).toString())
        } else {
            handle(request)
        }

    private fun insecureHttp(request: Request) =
        request.header("X-Forwarded-Proto")?.startsWith("https")?.not() == true

    private val probablyOnHeroku = System.getenv("DYNO") != null
}
