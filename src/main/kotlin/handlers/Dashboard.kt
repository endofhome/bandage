package handlers

import AuthenticatedUser
import Bandage.StaticConfig.view
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.http4k.template.ViewModel
import storage.AudioFileMetadata
import storage.BitRate
import storage.Duration
import storage.MetadataStorage
import java.math.BigDecimal
import java.util.UUID

object Dashboard {
    operator fun invoke(request: Request, metadataStorage: MetadataStorage): Response {
        val nowPlaying = request.query("id")?.let {
            metadataStorage.find(UUID.fromString(it))
        }?.viewModel()

        val folders = metadataStorage.all().groupBy { file ->
            file.path.drop(1).substringBefore("/")
         }.toSortedMap().map { folder ->
            ViewModels.Folder(folder.key, folder.value.map { audioFile -> audioFile.viewModel() })
        }

        return Response(OK).with(view of DashboardPage(AuthenticatedUser, folders, nowPlaying))
    }

    data class DashboardPage(val loggedInUser: AuthenticatedUser, val folders: List<ViewModels.Folder>, val nowPlaying: ViewModels.AudioFileMetadata? = null) : ViewModel {
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

    private fun String.emptyIfZero(): String =
        if (this != "0") "$this:"
        else ""

    private fun BitRate.presentationFormat(): String = (this.value.toBigDecimal() / BigDecimal(1000)).toString()

    private fun Duration.presentationFormat(): String {
        val (rawSeconds, _) = this.value.split(".")
        val duration = java.time.Duration.ofSeconds(rawSeconds.toLong())
        val hours = duration.toHoursPart().toString().emptyIfZero()
        val minutes = duration.toMinutesPart()
        val seconds = duration.toSecondsPart().toString().padStart(2, '0')
        return "$hours$minutes:$seconds"
    }

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
