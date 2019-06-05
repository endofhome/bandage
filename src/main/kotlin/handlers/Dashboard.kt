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
import java.util.UUID

object Dashboard {
    operator fun invoke(authenticatedRequest: AuthenticatedRequest, metadataStorage: MetadataStorage): Response {
        val nowPlaying = authenticatedRequest.request.query("id")?.let { id ->
            metadataStorage.find(UUID.fromString(id)).map { metadata -> metadata?.viewModel() }.orElse { null }
        }

        val folders = metadataStorage.all().map { all ->
            all.groupBy { file ->
                file.path.drop(1).substringBefore("/")
            }.toList().sortByReversedThenFolderNamesOnlyContainingLetters().map { (folderName, files) ->
                ViewModels.Folder(folderName, files.map { audioFile -> audioFile.viewModel() })
            }
        }

        return when (folders) {
            is Success -> Response(OK).with(view of DashboardPage(authenticatedRequest.user, folders.value, nowPlaying))
            is Failure -> Response(INTERNAL_SERVER_ERROR)
        }
    }

    data class DashboardPage(val loggedInUser: User, val folders: List<ViewModels.Folder>, val nowPlaying: ViewModels.AudioTrackMetadata? = null) : ViewModel {
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
        data class Folder(val name: String, val files: List<AudioTrackMetadata>)
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

private fun List<Pair<String, List<AudioTrackMetadata>>>.sortByReversedThenFolderNamesOnlyContainingLetters(): List<Pair<String, List<AudioTrackMetadata>>> {
    val (onlyLetters, others) = this.partition { (folderName) -> folderName.all { it.isLetter() } }
    return others.sortedBy { (folderName) -> folderName }.reversed() + onlyLetters
}
