package handlers

import AuthenticatedRequest
import Bandage
import DateTimePatterns
import User
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.with
import org.http4k.routing.path
import org.http4k.template.ViewModel
import result.map
import result.orElse
import storage.AudioTrackMetadata
import storage.MetadataStorage
import java.time.format.DateTimeFormatter
import java.util.UUID

object TrackMetadata {
    operator fun invoke(authenticatedRequest: AuthenticatedRequest, metadataStorage: MetadataStorage): Response {
        val trackMetadata = authenticatedRequest.request.path("id")?.let { id ->
            metadataStorage.findTrack(UUID.fromString(id)).map { it?.viewModel() }.orElse { null }
        } ?: return Response(Status.NOT_FOUND)

        return Response(Status.OK).with(Bandage.StaticConfig.view of TrackMetadataPage(authenticatedRequest.user, trackMetadata))
    }

    data class TrackMetadataPage(val loggedInUser: User, val trackMetadata: ViewModels.AudioTrackMetadata) : ViewModel {
        override fun template() = "track_metadata"
    }

    private fun AudioTrackMetadata.viewModel(): ViewModels.AudioTrackMetadata =
        this.let {
            with(AudioTrackMetadata) {
                val pattern = DateTimePatterns.shortPatternFor(it.recordedTimestampPrecision)
                val dateTimeFormatter = DateTimeFormatter.ofPattern(pattern)
                ViewModels.AudioTrackMetadata(
                    it.uuid.toString(),
                    it.artist,
                    it.title,
                    it.format,
                    it.bitRate?.presentationFormat(),
                    it.duration?.presentationFormat(),
                    it.playUrl.toString(),
                    it.recordedTimestamp.format(dateTimeFormatter),
                    it.uploadedTimestamp.format(dateTimeFormatter),
                    it.collections.map { it.title }.ifEmpty { null }
                )
            }
        }

    object ViewModels {
        data class AudioTrackMetadata(
            val uuid: String,
            val artist: String,
            val title: String,
            val format: String,
            val bitRate: String?,
            val duration: String?,
            val playUrl: String,
            val recordedTimestamp: String? = null,
            val uploadedTimestamp: String = "",
            val collections: List<String>? = null
        )
    }
}
