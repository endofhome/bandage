import org.http4k.core.Response
import org.http4k.core.Status
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object Logging {
    val logger: Logger = LoggerFactory.getLogger(Bandage::class.java)

    fun loggedResponse(status: Status, logMessage: String?, user: User) =
        Response(status).also { logger.warn("User ${user.userId}: $logMessage") }
}