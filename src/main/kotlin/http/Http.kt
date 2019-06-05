package http

import Bandage.StaticConfig.logger
import http.Host.LOCAL
import http.Host.PRODUCTION
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import java.io.PrintWriter
import java.io.StringWriter

enum class Host(val value: String) {
    PRODUCTION("band-age.herokuapp.com"),
    LOCAL("localhost")
}

fun host() = if (probablyOnHeroku) PRODUCTION else LOCAL

private val probablyOnHeroku = System.getenv("DYNO") != null

object Filters {
    object EnforceHttpsOnHeroku {
        operator fun invoke(): Filter = Filter { next -> { enforceHttps(next, it) } }

        private fun enforceHttps(handle: HttpHandler, request: Request): Response =
            if (insecureHttp(request) && probablyOnHeroku) {
                Response(Status.SEE_OTHER)
                    .header("Location", request.uri.copy(scheme = "https", host = PRODUCTION.value).toString())
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
