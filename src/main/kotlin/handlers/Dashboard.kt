package handlers

import AuthenticatedRequest
import Bandage.StaticConfig.view
import DateTimePatterns
import Logging.logger
import User
import org.http4k.core.Response
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.http4k.template.ViewModel
import result.Result.Failure
import result.Result.Success
import result.map
import storage.AudioTrackMetadataEnhancer
import storage.HasPreferredTitle.TitleType
import storage.HasPresentationFormat.Companion.presentationFormat
import storage.MetadataStorage
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Dashboard {
    operator fun invoke(
        authenticatedRequest: AuthenticatedRequest,
        metadataStorage: MetadataStorage,
        enableNewPlayerForEnvironment: Boolean
    ): Response {
        val request = authenticatedRequest.request
        val newPlayerEnabled =
            request.header("BANDAGE_ENABLE_NEW_PLAYER")?.toBoolean() ?: false
                    || request.query("newPlayer")?.toBoolean() ?: false
                    || enableNewPlayerForEnvironment

        val dateGroupedTracks = metadataStorage.tracks().map { all ->
            with(AudioTrackMetadataEnhancer) {
                all.groupBy { track ->
                    val localDate = track.recordedTimestamp.toLocalDate()
                    val pattern = DateTimePatterns.longPatternFor(track.recordedTimestampPrecision)
                    DateFormats(localDate, localDate.format(DateTimeFormatter.ofPattern(pattern)))
                }.map { (date, tracks) -> date to tracks.enhanceWithTakeNumber() }
                 .sortedBy { (date) -> date.localDate }
                 .reversed()
                 .map { (date, tracks) ->
                     ViewModels.DateGroup(
                         date.localDate.toString(),
                         date.formattedDate,
                         tracks.sortedBy { it.base.recordedTimestamp }
                               .reversed()
                               .map { audioFile -> audioFile.viewModel(newPlayerEnabled) }
                     )
                 }
            }
        }

        return when (dateGroupedTracks) {
            is Success -> {
                val nowPlaying = request.query("id")?.let { id ->
                    dateGroupedTracks.value.flatMap { it.tracks }.find { it.uuid == id }
                }
                val highlighted = request.query("highlighted")?.let { id ->
                    dateGroupedTracks.value.flatMap { it.tracks }.find { it.uuid == id }
                } ?: nowPlaying

                Response(OK).with(view of DashboardPage(authenticatedRequest.user, dateGroupedTracks.value, highlighted, nowPlaying))
            }
            is Failure -> {
                logger.warn(dateGroupedTracks.reason.message)
                Response(INTERNAL_SERVER_ERROR)
            }
        }
    }

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

    private fun AudioTrackMetadataEnhancer.EnhancedAudioTrackMetadata.viewModel(newPlayerEnabled: Boolean): ViewModels.AudioTrackMetadata =
        this.base.let {
            val (title, titleType) = it.preferredTitle()
            val dateTimePattern = DateTimeFormatter.ofPattern(
                DateTimePatterns.filenamePatternFor(it.recordedTimestampPrecision)
            )
            val dateTime = it.recordedTimestamp.format(dateTimePattern)

            ViewModels.AudioTrackMetadata(
                "${it.uuid}",
                title,
                titleType,
                it.format,
                it.bitRate?.presentationFormat(),
                it.duration?.presentationFormat(),
                "${it.playUrl}",
                "${it.playUrl}",
                listOf(
                    dateTime,
                    "$title${this.takeNumber?.let { " (take $it)" }.orEmpty()}"
                ).joinToString(" "),
                this.takeNumber?.let { take -> "$take" }.orEmpty(),
                if (newPlayerEnabled) { File("public/panarific_2.json").readText() } else null // TODO temporary, for testing
            )
        }

    object ViewModels {
        data class DateGroup(
            val date: String,
            val presentationDate: String,
            val tracks: List<AudioTrackMetadata>
        )

        data class AudioTrackMetadata(
            val uuid: String,
            val title: String,
            val titleType: TitleType,
            val format: String,
            val bitRate: String?,
            val duration: String?,
            val playUrl: String,
            val downloadUrl: String,
            val filename: String,
            val takeNumber: String,
            val peaks: String? // TODO temporary, for testing new audio player
        )
    }
}
