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
import result.map
import result.orElse
import storage.AudioTrackMetadata
import storage.AudioTrackMetadataEnhancer
import storage.HasPreferredTitle
import storage.HasPresentationFormat.Companion.presentationFormat
import storage.MetadataStorage
import java.io.File
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

object TrackMetadata {
    operator fun invoke(authenticatedRequest: AuthenticatedRequest, metadataStorage: MetadataStorage): Response {
        val newPlayerEnabled = authenticatedRequest.request.header("BANDAGE_ENABLE_NEW_PLAYER")?.toBoolean() ?: false || authenticatedRequest.request.query("newPlayer")?.toBoolean() ?: false

        val user = authenticatedRequest.user
        val trackMetadata = (authenticatedRequest.request.path("id")?.let { id ->
            val uuid = try { UUID.fromString(id) } catch (e: Exception) { return loggedResponse(NOT_FOUND, e.message, user) }
            val maybeFoundTrack = metadataStorage.findTrack(uuid)
            when (maybeFoundTrack) {
                is Success -> maybeFoundTrack.value ?: return loggedResponse(NOT_FOUND, "Track $id was not found in metadata storage", user)
                is Failure -> return loggedResponse(NOT_FOUND, maybeFoundTrack.reason.message, user)
            }
        } ?: return loggedResponse(NOT_FOUND, "Missing 'id' path parameter in request for track metadata", user))
        val trackMetadataViewModel = trackMetadata.viewModel(metadataStorage)

        val (title, titleType) = trackMetadataViewModel.preferredTitle()
        val playerMetadata = Dashboard.ViewModels.AudioTrackMetadata(
            trackMetadataViewModel.uuid,
            title,
            titleType,
            trackMetadataViewModel.format,
            trackMetadataViewModel.bitRate,
            trackMetadataViewModel.duration,
            trackMetadataViewModel.playUrl,
            trackMetadataViewModel.downloadUrl,
            trackMetadataViewModel.filename,
            with(AudioTrackMetadataEnhancer) {
                trackMetadata.enhanceWithTakeNumber(metadataStorage)
                    .map { it.takeNumber?.toString().orEmpty() }
                    .orElse { "" }
            },
            if (newPlayerEnabled) { File("public/panarific_2.json").readText() } else null // TODO temporary, for testing
        )

        return Response(Status.OK).with(Bandage.StaticConfig.view of TrackMetadataPage(authenticatedRequest.user, trackMetadataViewModel, playerMetadata))
    }

    data class TrackMetadataPage(val loggedInUser: User, val trackMetadata: ViewModels.AudioTrackMetadata, val playerMetadata: Dashboard.ViewModels.AudioTrackMetadata) : ViewModel {
        override fun template() = "track_metadata"
    }

    private fun AudioTrackMetadata.viewModel(metadataStorage: MetadataStorage): ViewModels.AudioTrackMetadata {
        val audioTrackMetadata = this
        val recordedPattern = DateTimePatterns.shortPatternFor(audioTrackMetadata.recordedTimestampPrecision)
        val uploadedPattern = DateTimePatterns.shortPatternFor(ChronoUnit.SECONDS)
        val dateTimePattern = DateTimeFormatter.ofPattern(
            DateTimePatterns.filenamePatternFor(audioTrackMetadata.recordedTimestampPrecision)
        )
        val dateTime = this.recordedTimestamp.format(dateTimePattern)

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
            downloadUrl.toString(),
            recordedTimestamp.format(DateTimeFormatter.ofPattern(recordedPattern)),
            uploadedTimestamp.format(DateTimeFormatter.ofPattern(uploadedPattern)),
            collections.map { it.title },
            with(AudioTrackMetadataEnhancer) {
                listOf(
                    dateTime,
                    "$title${audioTrackMetadata.enhanceWithTakeNumber(metadataStorage).map { it.takeNumber }.let { " (take $it)" }}"
                ).joinToString(" ")
            }
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
            val downloadUrl: String,
            val recordedTimestamp: String,
            val uploadedTimestamp: String,
            val collections: List<String>,
            val filename: String
        ) : HasPreferredTitle {
            override val workingTitles: List<String> = listOf(workingTitle)
        }
    }
}
