import Bandage.StaticConfig.configurationFilesDir
import Bandage.StaticConfig.defaultPort
import Bandage.StaticConfig.filters
import Bandage.StaticConfig.logger
import RouteMappings.dashboard
import RouteMappings.index
import RouteMappings.login
import RouteMappings.logout
import RouteMappings.play
import config.BandageConfig
import config.Configuration
import config.RequiredConfig
import config.ValidateConfig
import handlers.Dashboard
import handlers.Login
import handlers.Play
import http.Filters.CatchAll
import http.Filters.EnforceHttpsOnHeroku
import org.http4k.core.Body
import org.http4k.core.ContentType
import org.http4k.core.Method.GET
import org.http4k.core.Method.POST
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.core.then
import org.http4k.filter.ServerFilters.ReplaceResponseContentsWithStaticFile
import org.http4k.routing.ResourceLoader
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.routing.static
import org.http4k.server.Jetty
import org.http4k.server.asServer
import org.http4k.template.HandlebarsTemplates
import org.http4k.template.viewModel
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import storage.DropboxFileStorageFactory
import storage.FileStorage
import storage.FileStorageFactory
import storage.LocalCsvMetadataStorageFactory
import storage.MetadataStorage
import storage.MetadataStorageFactory
import java.nio.file.Path
import java.nio.file.Paths

fun main(args: Array<String>) {
    val port = if (args.isNotEmpty()) args[0].toInt() else defaultPort
    Bandage.init(BandageConfig, LocalCsvMetadataStorageFactory, DropboxFileStorageFactory).app.asServer(Jetty(port)).start()

    logger.info("Bandage has started on http://localhost:$port")
}

class Bandage(systemConfig: Configuration, metadataStorage: MetadataStorage, fileStorage: FileStorage) {
    companion object {
        fun init(requiredConfig: RequiredConfig, metadataStorageFactory: MetadataStorageFactory, fileStorageFactory: FileStorageFactory): Bandage =
            ValidateConfig(requiredConfig, configurationFilesDir).run {
                Bandage(this, metadataStorageFactory(this), fileStorageFactory(this))
            }
    }

    object StaticConfig {
        const val appName = "BANDAGE"
        private val renderer = HandlebarsTemplates().HotReload("src/main/resources")
        val view = Body.viewModel(renderer, ContentType.TEXT_HTML).toLens()
        val filters = EnforceHttpsOnHeroku()
                .then(ReplaceResponseContentsWithStaticFile(ResourceLoader.Directory("public")))
                .then(CatchAll())
        val configurationFilesDir: Path = Paths.get("configuration")
        const val defaultPort = 7000
        val logger: Logger = LoggerFactory.getLogger(Bandage::class.java)
    }

    private val userManagement = UserManagement(systemConfig)
    private val authentication = Authentication(systemConfig, userManagement)
    private fun redirectTo(location: String): (Request) -> Response = { Response(SEE_OTHER).header("Location", location) }

    private val routes = with(authentication) { routes(
            index       bind GET  to { request -> ifAuthenticated(request, then = redirectTo(dashboard)) },
            login       bind GET  to { request -> ifAuthenticated(request, then = redirectTo(index), otherwise = Login(userManagement)) },
            login       bind POST to { request -> authenticateUser(request) },
            logout      bind GET  to { logout() },
            dashboard   bind GET  to { request -> ifAuthenticated(request, then = { Dashboard(request, metadataStorage) }) },
            play        bind GET  to { request -> ifAuthenticated(request, then = { Play(request, metadataStorage, fileStorage) }, otherwise = Response(FORBIDDEN)) },

            "/public"   bind static(ResourceLoader.Directory("public"))
        )
    }

    val app = routes.withFilter(filters)
}
