package http

import Bandage.StaticConfig.defaultPort
import Bandage.StaticConfig.logger
import http.HttpConfig.PerEnvironment.LOCAL
import http.HttpConfig.PerEnvironment.PRODUCTION
import http.HttpConfig.probablyOnHeroku
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import java.io.PrintWriter
import java.io.StringWriter

object HttpConfig {
    val environment get() = if (probablyOnHeroku) PRODUCTION else LOCAL
    val probablyOnHeroku = System.getenv("DYNO") != null
    var port: Int = defaultPort

    enum class PerEnvironment(val config: HttpConfigData) {
        PRODUCTION(HttpConfigData("https", "band-age.herokuapp.com")),
        LOCAL(HttpConfigData("http", "localhost", port))
    }

    data class HttpConfigData(val protocol: String, val host: String, val port: Int? = null) {
        val baseUrl: String = "$protocol://$host${port?.let { ":$it" }.orEmpty()}"
    }
}

object Filters {
    object EnforceHttpsOnHeroku {
        operator fun invoke(): Filter = Filter { next -> { enforceHttpsOnHeroku(next, it) } }

        private fun enforceHttpsOnHeroku(handle: HttpHandler, request: Request): Response =
            if (insecureHttp(request) && probablyOnHeroku) {
                Response(Status.SEE_OTHER)
                    .header("Location", request.uri.copy(scheme = PRODUCTION.config.protocol).toString())
            } else {
                handle(request)
            }

        private fun insecureHttp(request: Request) =
            request.header("X-Forwarded-Proto")?.startsWith("https")?.not() == true
    }

    object CatchAll {
        operator fun invoke(errorStatus: Status = Status.INTERNAL_SERVER_ERROR): Filter =
            Filter { next ->
                {
                    try {
                        next(it)
                    } catch (e: Exception) {
                        val sw = StringWriter()
                        e.printStackTrace(PrintWriter(sw))
                        logger.error(sw.toString())
                        Response(errorStatus).body(sw.toString())
                    }
                }
            }
    }
}
