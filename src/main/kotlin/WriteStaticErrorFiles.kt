import http.HttpConfig
import org.http4k.template.ViewModel
import java.io.File

object WriteStaticErrorFiles {
    private val baseUrl = HttpConfig.environment.config.baseUrl
    private val errorResponses = listOf(
        ErrorResponse(400, "Bad Request", "Either you did something bad, or I did. \uD83E\uDD26\u200D", baseUrl),
        ErrorResponse(404, "Not Found", "The requested resource does not exist.", baseUrl),
        ErrorResponse(500, "Internal Server Error", "Apologies. Something went really, really wrong.", baseUrl)
    )
    operator fun invoke() {
        errorResponses.forEach {
            File("public/static-errors/${it.statusCode}").writeText(Bandage.StaticConfig.renderer(it))
        }
    }
}

data class ErrorResponse(val statusCode: Int, val statusDescription: String, val message: String, val baseUrl: String): ViewModel {
    override fun template() = "error"
}
