package handlers

import AuthenticatedRequest
import Bandage.StaticConfig.view
import DateTimePatterns
import User
import org.http4k.core.Response
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.http4k.template.ViewModel
import result.Result.Failure
import result.Result.Success
import result.map
import result.orElse
import storage.AudioTrackMetadata
import storage.AudioTrackMetadata.Companion.presentationFormat
import storage.MetadataStorage
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

object Dashboard {
    operator fun invoke(authenticatedRequest: AuthenticatedRequest, metadataStorage: MetadataStorage): Response {
        val request = authenticatedRequest.request
        val nowPlaying = request.query("id")?.let { id ->
            metadataStorage.findTrackViewModelOrNull(id)
        }
        val highlighted = request.query("highlighted")?.let { id ->
            metadataStorage.findTrackViewModelOrNull(id)
        } ?: nowPlaying

        val tracks = metadataStorage.tracks().map { all ->
            all.groupBy { track ->
                val localDate = track.recordedTimestamp.toLocalDate()
                val pattern = DateTimePatterns.longPatternFor(track.recordedTimestampPrecision)
                DateFormats(localDate, localDate.format(DateTimeFormatter.ofPattern(pattern)))
            }.toList()
             .sortedBy { (date) -> date.localDate }
             .reversed()
             .map { (date, tracks) ->
                 ViewModels.DateGroup(
                     date.localDate.toString(),
                     date.formattedDate,
                     tracks.sortedBy { it.recordedTimestamp }.reversed().map { audioFile -> audioFile.viewModel() }
                 )
             }
        }

        return when (tracks) {
            is Success -> Response(OK).with(view of DashboardPage(authenticatedRequest.user, tracks.value, highlighted, nowPlaying))
            is Failure -> Response(INTERNAL_SERVER_ERROR)
        }
    }

    private fun MetadataStorage.findTrackViewModelOrNull(uuid: String) =
        findTrack(UUID.fromString(uuid)).map { metadata -> metadata?.viewModel() }.orElse { null }

    data class DateFormats(val localDate: LocalDate, val formattedDate: String)

    data class DashboardPage(
        val loggedInUser: User,
        val dateGroups: List<ViewModels.DateGroup>,
        val highlighted: ViewModels.AudioTrackMetadata? = null,
        val nowPlaying: ViewModels.AudioTrackMetadata? = null,
        val autoPlayAudio: Boolean = true
    ) : ViewModel {
        override fun template() = "dashboard"
    }

    private fun AudioTrackMetadata.viewModel(): ViewModels.AudioTrackMetadata =
        ViewModels.AudioTrackMetadata(
            "$uuid",
            title,
            format,
            bitRate?.presentationFormat(),
            duration?.presentationFormat(),
            "$playUrl"
        )

    object ViewModels {

        data class DateGroup(
            val date: String,
            val presentationDate: String,
            val tracks: List<AudioTrackMetadata>
        )

        data class AudioTrackMetadata(
            val uuid: String,
            val title: String,
            val format: String,
            val bitRate: String?,
            val duration: String?,
            val playUrl: String
        )
    }
}
