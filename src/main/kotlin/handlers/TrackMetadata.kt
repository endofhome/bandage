package handlers

import AuthenticatedRequest
import Bandage
import DateTimePatterns
import Logging.loggedResponse
import User
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.with
import org.http4k.routing.path
import org.http4k.template.ViewModel
import result.Result.Failure
import result.Result.Success
import storage.AudioTrackMetadata
import storage.HasPreferredTitle
import storage.HasPresentationFormat.Companion.presentationFormat
import storage.MetadataStorage
import java.time.format.DateTimeFormatter
import java.util.UUID

object TrackMetadata {
    operator fun invoke(authenticatedRequest: AuthenticatedRequest, metadataStorage: MetadataStorage): Response {
        val user = authenticatedRequest.user
        val trackMetadata = authenticatedRequest.request.path("id")?.let { id ->
            val foundTrack = metadataStorage.findTrack(UUID.fromString(id))
            when (foundTrack) {
                is Success -> foundTrack.value?.viewModel() ?: return loggedResponse(NOT_FOUND, "Track $id was not found in metadata storage", user)
                is Failure -> return loggedResponse(NOT_FOUND, foundTrack.reason.message, user)
            }

        } ?: return loggedResponse(NOT_FOUND, "Missing 'id' path parameter in request for track metadata", user)

        val (title, titleType) = trackMetadata.preferredTitle()
        val playerMetadata = Dashboard.ViewModels.AudioTrackMetadata(
            trackMetadata.uuid,
            title,
            titleType,
            trackMetadata.format,
            trackMetadata.bitRate,
            trackMetadata.duration,
            trackMetadata.playUrl,
            trackMetadata.playUrl
        )

        return Response(Status.OK).with(Bandage.StaticConfig.view of TrackMetadataPage(authenticatedRequest.user, trackMetadata, playerMetadata))
    }

    data class TrackMetadataPage(val loggedInUser: User, val trackMetadata: ViewModels.AudioTrackMetadata, val playerMetadata: Dashboard.ViewModels.AudioTrackMetadata) : ViewModel {
        override fun template() = "track_metadata"
    }

    private fun AudioTrackMetadata.viewModel(): ViewModels.AudioTrackMetadata {
        val pattern = DateTimePatterns.shortPatternFor(this.recordedTimestampPrecision)
        val dateTimeFormatter = DateTimeFormatter.ofPattern(pattern)

        return ViewModels.AudioTrackMetadata(
            uuid.toString(),
            artist,
            preferredTitle().first,
            title,
            workingTitles.firstOrNull().orEmpty(),
            format,
            bitRate?.presentationFormat(),
            duration?.presentationFormat(),
            playUrl.toString(),
            recordedTimestamp.format(dateTimeFormatter),
            uploadedTimestamp.format(dateTimeFormatter),
            collections.map { it.title }
        )
    }

    object ViewModels {
        data class AudioTrackMetadata(
            val uuid: String,
            val artist: String,
            val heading: String,
            override val title: String,
            val workingTitle: String,
            val format: String,
            val bitRate: String?,
            val duration: String?,
            val playUrl: String,
            val recordedTimestamp: String,
            val uploadedTimestamp: String,
            val collections: List<String>
        ) : HasPreferredTitle {
            override val workingTitles: List<String> = listOf(workingTitle)
        }
    }
}
