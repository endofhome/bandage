import Bandage.StaticConfig.logger
import org.http4k.core.Response
import org.http4k.core.Status

object Logging {
    fun loggedResponse(status: Status, logMessage: String?, user: User) =
        Response(status).also { logger.warn("User ${user.userId}: $logMessage") }
}