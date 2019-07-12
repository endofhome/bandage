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
import storage.HasPreferredTitle
import storage.MetadataStorage
import java.time.format.DateTimeFormatter
import java.util.UUID

object TrackMetadata {
    operator fun invoke(authenticatedRequest: AuthenticatedRequest, metadataStorage: MetadataStorage): Response {
        val trackMetadata = authenticatedRequest.request.path("id")?.let { id ->
            metadataStorage.findTrack(UUID.fromString(id)).map { it?.viewModel() }.orElse { null }
        } ?: return Response(Status.NOT_FOUND)

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

    private fun AudioTrackMetadata.viewModel(): ViewModels.AudioTrackMetadata =
        this.let {
            with(AudioTrackMetadata) {
                val pattern = DateTimePatterns.shortPatternFor(it.recordedTimestampPrecision)
                val dateTimeFormatter = DateTimeFormatter.ofPattern(pattern)
                ViewModels.AudioTrackMetadata(
                    it.uuid.toString(),
                    it.artist,
                    it.preferredTitle().first,
                    it.title,
                    it.workingTitles.firstOrNull().orEmpty(),
                    it.format,
                    it.bitRate?.presentationFormat(),
                    it.duration?.presentationFormat(),
                    it.playUrl.toString(),
                    it.recordedTimestamp.format(dateTimeFormatter),
                    it.uploadedTimestamp.format(dateTimeFormatter),
                    it.collections.map { it.title }
                )
            }
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
