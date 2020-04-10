package handlers

import AuthenticatedRequest
import Bandage
import Bandage.StaticConfig.disallowedFileExtensions
import PreProcessMetadata
import RouteMappings.upload
import User
import handlers.Upload.dayRange
import handlers.Upload.hourRange
import handlers.Upload.timeRange
import handlers.UploadPreview.ViewModels
import org.http4k.core.MultipartFormBody
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.core.with
import org.http4k.template.ViewModel
import storage.HasPresentationFormat.Companion.presentationFormat
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

object UploadPreview {
    val months = java.time.Month.values().map {
        Month(it.value, it.getDisplayName(TextStyle.FULL, Locale.UK))
    }
    val tempDir = File("/tmp/${Bandage.StaticConfig.appName.toLowerCase()}")

    operator fun invoke(authenticatedRequest: AuthenticatedRequest, artistOverride: String = "", clock: Clock): Response {
        val formFile = try {
            val body = MultipartFormBody.from(authenticatedRequest.request)
            body.file("file") ?: return Response(BAD_REQUEST)
        } catch (e: Exception) {
            return Response(BAD_REQUEST)
        }

        val filename = formFile.filename.substringAfterLast('/')
        val file = File("$tempDir/$filename")

        if (disallowedFileExtensions.contains(file.extension)) {
            return Response(SEE_OTHER)
                .header("Location", "$upload?unsupported-file-type=${file.extension}")
        }

        if (!tempDir.exists()) {
            tempDir.mkdir()
        }

        formFile.content.use { inputstream ->
            Files.copy(
                inputstream,
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }

        val preProcessedAudioTrackMetadata = PreProcessMetadata(file, artistOverride)
        val (year, month, day, hour, minute, second) = if (preProcessedAudioTrackMetadata.recordedTimestamp != null && preProcessedAudioTrackMetadata.recordedTimestampPrecision != null) {
            DisassembleTimestamp(preProcessedAudioTrackMetadata.recordedTimestamp, preProcessedAudioTrackMetadata.recordedTimestampPrecision)
        } else {
            val now = clock.instant().atZone(ZoneOffset.UTC)
            DisassembledTimestamp(now.year, now.monthValue, now.dayOfMonth, null, null, null)
        }
        val trackMetadata = ViewModels.PreProcessedAudioTrackMetadata(
            System.getenv("${Bandage.StaticConfig.appName.toUpperCase()}_ARTIST_OVERRIDE") ?: preProcessedAudioTrackMetadata.artist,
            preProcessedAudioTrackMetadata.workingTitle, // TODO not required
            null,
            preProcessedAudioTrackMetadata.workingTitle,
            preProcessedAudioTrackMetadata.format,
            preProcessedAudioTrackMetadata.bitRate?.presentationFormat(),
            preProcessedAudioTrackMetadata.bitRate?.value,
            preProcessedAudioTrackMetadata.duration?.presentationFormat(),
            preProcessedAudioTrackMetadata.duration?.value,
            preProcessedAudioTrackMetadata.hash,
            filename,
            preProcessedAudioTrackMetadata.normalisedFileSize,
            year,
            month,
            day,
            hour?.toString()?.padStart(2, '0'),
            minute?.toString()?.padStart(2, '0'),
            second?.toString()?.padStart(2, '0')
        )
        val viewModel = PreviewUploadTrackMetadataPage(
            authenticatedRequest.user,
            trackMetadata,
            months,
            dayRange.toList(),
            hourRange.map { it.toString().padStart(2, '0') }.toList(),
            timeRange.map { it.toString().padStart(2, '0') }.toList(),
            timeRange.map { it.toString().padStart(2, '0') }.toList()
        )

        return Response(OK).with(Bandage.StaticConfig.view of viewModel)
        /*
            TODO need to upload the file somewhere immediately, in case the app crashes/is shut down and replaced
            and then when the user uploads, just add the metadata, and possibly move the file
            if the user doesn't upload in a certain time frame, delete the file.
        */
    }

    object ViewModels {
        data class PreProcessedAudioTrackMetadata(
            val artist: String?,
            val heading: String?,
            val title: String?,
            val workingTitle: String?,
            val format: String,
            val bitRate: String?,
            val bitRateRaw: String?,
            val duration: String?,
            val durationRaw: String?,
            val hash: String,
            val filename: String,
            val normalisedFileSize: Long,
            val recordedYear: Int,
            val recordedMonth: Int?,
            val recordedDay: Int?,
            val recordedHour: String?,
            val recordedMinute: String?,
            val recordedSecond: String?
        )
    }
}

data class PreviewUploadTrackMetadataPage(
    val loggedInUser: User,
    val trackMetadata: ViewModels.PreProcessedAudioTrackMetadata,
    val months: List<Month>,
    val days: List<Int>,
    val hours: List<String>,
    val minutes: List<String>,
    val seconds: List<String>
) : ViewModel {
    override fun template() = "preview_upload_track_metadata"
}

data class Month(val number: Int, val name: String)
