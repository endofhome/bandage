package handlers

import AuthenticatedRequest
import Logging.loggedResponse
import RouteMappings.dashboard
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.core.body.formAsMap
import org.http4k.routing.path
import result.Result.Failure
import result.Result.Success
import result.map
import result.orElse
import storage.MetadataStorage
import java.util.UUID

object EditTrackMetadata {
    operator fun invoke(authenticatedRequest: AuthenticatedRequest, metadataStorage: MetadataStorage): Response {
        val user = authenticatedRequest.user
        val id = try { authenticatedRequest.request.path("id") } catch (e: Exception) { return loggedResponse(BAD_REQUEST, e.message, user) }
        val uuid = try { UUID.fromString(id) } catch (e: Exception) { return loggedResponse(BAD_REQUEST, e.message, user) }
        val formAsMap = authenticatedRequest.request.formAsMap()
        val newTitle = "title".let { formAsMap[it]?.single() ?: return loggedResponse(BAD_REQUEST, missingFormFieldMessage(it), user) }
        val newWorkingTitle = "working_title".let { formAsMap[it]?.single() ?: return loggedResponse(BAD_REQUEST, missingFormFieldMessage(it), user) }

        val foundTrackResult = metadataStorage.findTrack(uuid)
        val foundTrack = when (foundTrackResult) {
            is Success -> foundTrackResult.value ?: return loggedResponse(NOT_FOUND,"Track $uuid was not found in metadata storage",user)
            is Failure -> return loggedResponse(NOT_FOUND, foundTrackResult.reason.message, user)
        }

        val updatedTrack = foundTrack.copy(
            title = newTitle,
            workingTitles = listOf(newWorkingTitle)
        )

        return metadataStorage.updateTrack(updatedTrack).map {
            Response(SEE_OTHER).header("Location", "$dashboard?highlighted=$uuid#$uuid")
        }.orElse { loggedResponse(INTERNAL_SERVER_ERROR, it.message, user) }
    }

    private fun missingFormFieldMessage(fieldName: String) = "'$fieldName' field was unavailable when handling request to edit track metadata"
}
