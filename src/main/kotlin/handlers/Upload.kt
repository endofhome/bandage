package handlers

import Logging.logger
import RouteMappings.dashboard
import handlers.UploadPreview.ViewModels.PreProcessedAudioTrackMetadata
import handlers.UploadPreview.tempDir
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.core.body.formAsMap
import result.flatMap
import result.map
import result.orElse
import storage.AudioTrackMetadata
import storage.FileStorage
import storage.FileStoragePermission.PasswordProtected
import storage.HasPresentationFormat.Companion.presentationFormat
import storage.MetadataStorage
import storage.toBitRate
import storage.toDuration
import java.io.File
import java.time.Instant
import java.time.ZoneOffset.UTC
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

object Upload {
    operator fun invoke(request: Request, metadataStorage: MetadataStorage, fileStorage: FileStorage, fileStoragePassword: String): Response {
        val preProcessedAudioTrackMetadata: PreProcessedAudioTrackMetadata = try {
            val formAsMap = request.formAsMap()

            val artist = formAsMap.singleOrLog("artist") ?: return Response(BAD_REQUEST)
            val workingTitle = formAsMap.singleOrLog("working_title") ?: return Response(BAD_REQUEST)
            val duration = formAsMap.singleOrLog("duration_raw") ?: return Response(BAD_REQUEST)
            val format = formAsMap.singleOrLog("format") ?: return Response(BAD_REQUEST)
            val bitRate = formAsMap.singleOrLog("bitrate_raw") ?: return Response(BAD_REQUEST)
            val recordedTimestamp = formAsMap.singleOrLog("recordedOn") ?: return Response(BAD_REQUEST)
            val filename = formAsMap.singleOrLog("filename") ?: return Response(BAD_REQUEST)

            PreProcessedAudioTrackMetadata(
                artist,
                null,
                workingTitle,
                format,
                bitRate.toBitRate().presentationFormat(),
                bitRate,
                duration.toDuration().presentationFormat(),
                duration,
                recordedTimestamp,
                "some hash",
                filename
            )
        } catch (e: Exception) {
            logger.warn(e.message)
            return Response(BAD_REQUEST)
        }

        val recordedTimestamp = preProcessedAudioTrackMetadata.recordedTimestamp?.let { if (it != "") ZonedDateTime.parse(it) else null }
            ?: ZonedDateTime.now() // TODO default will go away once this is handled properly in separate fields. Or perhaps no timestamp should be allowed?

        val foldername = recordedTimestamp.toFoldername()
        val destinationPath = "/$foldername/${preProcessedAudioTrackMetadata.filename}"
        val tempFile = File("$tempDir/${preProcessedAudioTrackMetadata.filename}")

        return fileStorage.uploadFile(tempFile, destinationPath).flatMap {
            fileStorage.publicLink(destinationPath, PasswordProtected(fileStoragePassword)).map { passwordProtectedLink ->
                val uuid = UUID.randomUUID()
                metadataStorage.addTracks(
                    listOf(
                        AudioTrackMetadata(
                            uuid,
                            preProcessedAudioTrackMetadata.artist.orEmpty(),
                            "",
                            "untitled",
                            preProcessedAudioTrackMetadata.workingTitle?.let { listOf(it) }.orEmpty(),
                            preProcessedAudioTrackMetadata.format,
                            preProcessedAudioTrackMetadata.bitRateRaw?.toBitRate(),
                            preProcessedAudioTrackMetadata.durationRaw?.toDuration(),
                            tempFile.length().toInt(),
                            "",
                            recordedTimestamp,
                            ChronoUnit.SECONDS, // TODO
                            Instant.now().atZone(UTC),
                            passwordProtectedLink,
                            destinationPath,
                            preProcessedAudioTrackMetadata.hash,
                            emptyList()
                        )
                    )
                )
                uuid
            }.map { uuid ->
                Response(SEE_OTHER).header("Location", "$dashboard?highlighted=$uuid}")
            }
        }.orElse {
            logger.warn(it.message)
            Response(INTERNAL_SERVER_ERROR)
        }
    }

    private fun ZonedDateTime.toFoldername(): String = "$year-${monthValue.pad()}-${dayOfMonth.pad()}"

    private fun Int.pad() = toString().padStart(2, '0')

    private fun Map<String, List<String?>>.singleOrLog(field: String): String? {
        val extracted = this[field]?.single()
        return if (extracted != null) {
            extracted
        } else {
            logger.warn("Failure to extract '$field' field from form during file upload")
            null
        }
    }
}
