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

object EditMetadata {
    operator fun invoke(authenticatedRequest: AuthenticatedRequest, metadataStorage: MetadataStorage): Response =
        authenticatedRequest.request.path("id")?.let { id ->
            val uuid = try { UUID.fromString(id) } catch (e: Exception) { return Response(BAD_REQUEST) }
            val formAsMap = authenticatedRequest.request.formAsMap()
            val newTitle = formAsMap["title"]?.single() ?: return Response(BAD_REQUEST)

            val foundTrack = metadataStorage.findTrack(uuid).map { it }.orElse { null }
                ?: return Response(NOT_FOUND)
            val updatedTrack = foundTrack.copy(title = newTitle)
            metadataStorage.updateTrack(updatedTrack).map {
                Response(SEE_OTHER).header("Location", "$dashboard#$uuid")
            }.orElse {
                Response(INTERNAL_SERVER_ERROR)
            }
        } ?: Response(BAD_REQUEST)
}
