package handlers

import AuthenticatedRequest
import Bandage.StaticConfig.view
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
import java.time.format.DateTimeFormatter
import java.util.UUID

object Dashboard {
    operator fun invoke(authenticatedRequest: AuthenticatedRequest, metadataStorage: MetadataStorage): Response {
        val nowPlaying = authenticatedRequest.request.query("id")?.let { id ->
            metadataStorage.findTrack(UUID.fromString(id)).map { metadata -> metadata?.viewModel() }.orElse { null }
        }

        val tracks = metadataStorage.tracks().map { all ->
            all.groupBy { track ->
                track.recordedTimestamp.toLocalDate()
            }.toList().sortedBy { (date) -> date }.reversed().map { (date, tracks) ->
                ViewModels.DateGroup(
                    date.toString(),
                    date.dayOfMonth.toString(),
                    with(Ordinal) { date.dayOfMonth.dayOfMonthToOrdinal() },
                    date.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    tracks.sortedBy { it.recordedTimestamp }.reversed().map { audioFile -> audioFile.viewModel() }
                )
            }
        }

        return when (tracks) {
            is Success -> Response(OK).with(view of DashboardPage(authenticatedRequest.user, tracks.value, nowPlaying))
            is Failure -> Response(INTERNAL_SERVER_ERROR)
        }
    }

    data class DashboardPage(val loggedInUser: User, val dateGroups: List<ViewModels.DateGroup>, val nowPlaying: ViewModels.AudioTrackMetadata? = null) : ViewModel {
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
            val presentationDay: String,
            val presentationOrdinal: String,
            val presentationMonthYear: String,
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

    object Ordinal {
        fun Int.dayOfMonthToOrdinal(): String {
            require(this in 1..31) { "Day of month cannot be less than 0 or more than 31" }

            val lastTwo = this.toString().takeLast(2)
            return when {
                lastTwo.length == 2 && lastTwo.first() == '1' -> "th"
                lastTwo.last() == '1'                         -> "st"
                lastTwo.last() == '2'                         -> "nd"
                lastTwo.last() == '3'                         -> "rd"
                else                                          -> "th"
            }
        }
    }
}
