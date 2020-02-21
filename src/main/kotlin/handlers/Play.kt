package handlers

import DateTimePatterns
import Logging.logger
import RouteMappings.play
import StreamWithLength
import Tagger
import Tagger.Mode.AddId3Tags
import org.http4k.core.Headers
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.INTERNAL_SERVER_ERROR
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.SEE_OTHER
import org.http4k.routing.path
import result.Result.Failure
import result.Result.Success
import result.map
import result.orElse
import storage.AudioTrackMetadataEnhancer
import storage.FileStorage
import storage.MetadataStorage
import java.time.format.DateTimeFormatter
import java.util.UUID


object Play {
    operator fun invoke(
        request: Request,
        metadataStorage: MetadataStorage,
        fileStorage: FileStorage,
        disableId3Tagging: Boolean
    ): Response {
        request.query("id")?.let { return Response(SEE_OTHER).header("Location", "$play/$it") }
        val uuid = request.path("id") ?: return Response(BAD_REQUEST)

        val metadata = metadataStorage.findTrack(UUID.fromString(uuid))
            .map { it }
            .orElse { null }
            ?: return Response(NOT_FOUND)
        val enhancedMetadata =
            when (val enhanced = with(AudioTrackMetadataEnhancer) { metadata.enhanceWithTakeNumber(metadataStorage) }) {
                is Success -> enhanced.value
                is Failure -> return Response(INTERNAL_SERVER_ERROR)
            }

        return fileStorage.stream(metadata.passwordProtectedLink).map { audioStream ->
            val dateTimePattern = DateTimeFormatter.ofPattern(
                DateTimePatterns.filenamePatternFor(metadata.recordedTimestampPrecision)
            )
            val dateTime = metadata.recordedTimestamp.format(dateTimePattern)
            val (title: String) = enhancedMetadata.base.preferredTitle()

            // TODO the downloader that provides the inputstream should be closed.
            val (inputstream, streamLength) = if (disableId3Tagging || metadata.normalisedFileSize == null) {
                print("using original stream as ")
                if (metadata.normalisedFileSize == null) {
                    println("normalised file size for ${metadata.uuid} is null")
                } else {
                    println("id3 on-the-fly override is enabled")
                }
                StreamWithLength(audioStream, enhancedMetadata.base.fileSize.toLong())
            } else {
                println("adding id3 tags on-the-fly")
                with(Tagger) {
                    audioStream.manipulate(AddId3Tags(metadata))
                }
            }

            val headers: Headers = listOf(
                "Accept-Ranges" to "bytes",
                "Content-Length" to streamLength.toString(),
                "Content-Range" to "bytes 0-${streamLength - 1}/$streamLength",
                // TODO set this value dynamically depending on the codec used
                "Content-Type" to "audio/mpeg",
                "X-Content-Type-Options" to "nosniff",
                "Content-Disposition" to "attachment; filename=\"${
                listOf(
                    dateTime,
                    "$title${enhancedMetadata.takeNumber?.let { " (take $it)" }.orEmpty()}"
                ).joinToString(" ")
                }.${metadata.format}\""
            )

            Response(OK).body(inputstream, streamLength).headers(headers)
        }.orElse {
            logger.warn(it.message)
            Response(NOT_FOUND)
        }
    }
}
