package handlers

import AuthenticatedRequest
import RouteMappings.dashboard
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.core.body.formAsMap
import org.http4k.routing.path
import result.map
import result.orElse
import storage.MetadataStorage
import java.util.UUID

object EditTrackMetadata {
    operator fun invoke(authenticatedRequest: AuthenticatedRequest, metadataStorage: MetadataStorage): Response {
        val id = try { authenticatedRequest.request.path("id") } catch (e: Exception) { return Response(BAD_REQUEST) }
        val uuid = try { UUID.fromString(id) } catch (e: Exception) { return Response(BAD_REQUEST) }
        val formAsMap = authenticatedRequest.request.formAsMap()
        val newTitle = formAsMap["title"]?.single() ?: return Response(BAD_REQUEST)
        val newWorkingTitle = formAsMap["working_title"]?.single() ?: return Response(BAD_REQUEST)

        val foundTrack = metadataStorage.findTrack(uuid).map { it }.orElse { null }
            ?: return Response(NOT_FOUND)
        val updatedTrack = foundTrack.copy(
            title = newTitle,
            workingTitles = listOf(newWorkingTitle)
        )

        return metadataStorage.updateTrack(updatedTrack).map {
            Response(SEE_OTHER).header("Location", "$dashboard?highlighted=$uuid#$uuid")
        }.orElse {
            Response(INTERNAL_SERVER_ERROR)
        }
    }
}
