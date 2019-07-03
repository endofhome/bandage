package handlers

import DateTimePatterns
import RouteMappings.play
import org.http4k.core.Headers
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.routing.path
import result.Result
import result.Result.Failure
import result.Result.Success
import result.asSuccess
import result.flatMap
import result.map
import result.orElse
import storage.AudioTrackMetadata
import storage.FileStorage
import storage.MetadataStorage
import java.time.format.DateTimeFormatter
import java.util.UUID

object Play {
    operator fun invoke(
        request: Request,
        metadataStorage: MetadataStorage,
        fileStorage: FileStorage
    ): Response {
        request.query("id")?.let { return Response(SEE_OTHER).header("Location", "$play/$it") }

        val uuid = request.path("id") ?: return Response(BAD_REQUEST)
        val metadata = metadataStorage.findTrack(UUID.fromString(uuid)).map { it }.orElse { null } ?: return Response(NOT_FOUND)
        val enhancedMetadata = when (val enhanced = metadata.enhanceWithTakeNumber(metadataStorage)) {
            is Success -> enhanced.value
            is Failure -> return Response(INTERNAL_SERVER_ERROR)
        }

        return fileStorage.stream(metadata.passwordProtectedLink).map { audioStream ->
            val dateTimePattern = DateTimeFormatter.ofPattern(
                DateTimePatterns.filenamePatternFor(metadata.recordedTimestampPrecision)
            )
            val dateTime = metadata.recordedTimestamp.format(dateTimePattern)
            val headers: Headers = listOf(
                "Accept-Ranges" to "bytes",
                "Content-Length" to metadata.fileSize.toString(),
                "Content-Range" to "bytes 0-${metadata.fileSize - 1}/${metadata.fileSize}",
                "content-disposition" to "attachment; filename=${
                    listOf(dateTime, "${enhancedMetadata.basicMetadata.title}${enhancedMetadata.takeNumber?.let { " (take $it)" }.orEmpty()}").joinToString(" ")
                }.${metadata.format}"
            )
            Response(OK).body(audioStream).headers(headers)
        }.orElse { Response(NOT_FOUND) }
    }

    private fun AudioTrackMetadata.enhanceWithTakeNumber(metadataStorage: MetadataStorage): Result<Error, EnhancedAudioTrackMetadata> =
        metadataStorage.tracks()
            .map { tracks -> tracks.filter { it.recordedTimestamp.toLocalDate() == recordedTimestamp.toLocalDate() } }
            .flatMap { tracks ->
                val tracksWithSameName = tracks.filter { it.title.toLowerCase() == title.toLowerCase() }

                if (tracksWithSameName.size > 1) {
                    tracksWithSameName.sortedBy { it.recordedTimestamp }.mapIndexed { index, audioTrackMetadata ->
                        EnhancedAudioTrackMetadata(audioTrackMetadata, index + 1)
                    }.find { it.basicMetadata.uuid == this.uuid }?.asSuccess() ?: Failure(Error("Could not enhance ${this.uuid} with take number."))
                } else {
                    EnhancedAudioTrackMetadata(this).asSuccess()
                }
            }

    data class EnhancedAudioTrackMetadata(val basicMetadata: AudioTrackMetadata, val takeNumber: Int? = null)
}
