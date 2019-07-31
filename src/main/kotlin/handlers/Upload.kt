package handlers

import AuthenticatedRequest
import Bandage
import Logging.loggedResponse
import PreProcessMetadata
import User
import handlers.UploadPreview.ViewModels.PreProcessedAudioTrackMetadata
import org.http4k.core.MultipartFormBody
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.http4k.template.ViewModel
import storage.HasPresentationFormat.Companion.presentationFormat
import java.io.File

object UploadPreview {
    operator fun invoke(authenticatedRequest: AuthenticatedRequest, artistOverride: String = ""): Response {
        val user = authenticatedRequest.user
        val formFile = try {
            val body = MultipartFormBody.from(authenticatedRequest.request)
            body.file("file") ?: return loggedResponse(BAD_REQUEST, "Missing 'file' form part when previewing upload metadata", user)
        } catch (e: Exception) {
            return loggedResponse(BAD_REQUEST, e.message, user)
        }
        val fileBytes = formFile.content.use { inputstream ->
            inputstream.readAllBytes()
        }

        val tempDir = File("/tmp/${Bandage.StaticConfig.appName.toLowerCase()}")
        if (!tempDir.exists()) {
            tempDir.mkdir()
        }

        val file = File("$tempDir/${formFile.filename.substringAfterLast('/')}").also { it.writeBytes(fileBytes) }
        val preProcessedAudioTrackMetadata = PreProcessMetadata(file, artistOverride)
        val trackMetadata = PreProcessedAudioTrackMetadata(
            preProcessedAudioTrackMetadata.artist,
            preProcessedAudioTrackMetadata.workingTitle,
            preProcessedAudioTrackMetadata.workingTitle,
            preProcessedAudioTrackMetadata.format,
            preProcessedAudioTrackMetadata.bitRate?.presentationFormat(),
            preProcessedAudioTrackMetadata.duration?.presentationFormat(),
            preProcessedAudioTrackMetadata.recordedTimestamp?.toString() // TODO should be separate values
        )
        val viewModel = PreviewUploadTrackMetadataPage(authenticatedRequest.user, trackMetadata)

        return Response(OK).with(Bandage.StaticConfig.view of viewModel)
        // TODO need to upload the file somewhere immediately, in case the app crashes/is shut down and replaced
        // TODO and then when the user uploads, just add the metadata, and possibly move the file
        // TODO if the user doesn't upload in a certain timeframe, delete the file.
    }

    object ViewModels {
        data class PreProcessedAudioTrackMetadata(
            val artist: String?,
            val heading: String?,
            val workingTitle: String?,
            val format: String,
            val bitRate: String?,
            val duration: String?,
            val recordedTimestamp: String? // TODO should be separate values for each time unit
        )
    }
}

data class PreviewUploadTrackMetadataPage(val loggedInUser: User, val trackMetadata: PreProcessedAudioTrackMetadata) : ViewModel {
    override fun template() = "preview_upload_track_metadata"
}
