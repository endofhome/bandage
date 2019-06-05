package handlers

import AuthenticatedRequest
import Bandage.StaticConfig.view
import User
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.http4k.template.ViewModel
import storage.AudioFileMetadata
import storage.AudioFileMetadata.Companion.presentationFormat
import storage.MetadataStorage
import java.util.UUID

object Dashboard {
    operator fun invoke(authenticatedRequest: AuthenticatedRequest, metadataStorage: MetadataStorage): Response {
        val nowPlaying = authenticatedRequest.request.query("id")?.let {
            metadataStorage.find(UUID.fromString(it))
        }?.viewModel()

        val folders = metadataStorage.all().groupBy { file ->
            file.path.drop(1).substringBefore("/")
         }.toList().sortByReversedThenFolderNamesOnlyContainingLetters().map { (folderName, files) ->
            ViewModels.Folder(folderName, files.map { audioFile -> audioFile.viewModel() })
        }

        return Response(OK).with(view of DashboardPage(authenticatedRequest.user, folders, nowPlaying))
    }

    data class DashboardPage(val loggedInUser: User, val folders: List<ViewModels.Folder>, val nowPlaying: ViewModels.AudioFileMetadata? = null) : ViewModel {
        override fun template() = "dashboard"
    }

    private fun AudioFileMetadata.viewModel(): ViewModels.AudioFileMetadata =
        ViewModels.AudioFileMetadata(
            this.uuid.toString(),
            this.title,
            this.format,
            this.bitRate?.presentationFormat(),
            this.duration?.presentationFormat()
        )

    object ViewModels {
        data class Folder(val name: String, val files: List<AudioFileMetadata>)
        data class AudioFileMetadata(
            val uuid: String,
            val title: String,
            val format: String,
            val bitRate: String?,
            val duration: String?
        )
    }
}

private fun List<Pair<String, List<AudioFileMetadata>>>.sortByReversedThenFolderNamesOnlyContainingLetters(): List<Pair<String, List<AudioFileMetadata>>> {
    val (onlyLetters, others) = this.partition { (folderName) -> folderName.all { it.isLetter() } }
    return others.sortedBy { (folderName) -> folderName }.reversed() + onlyLetters
}
