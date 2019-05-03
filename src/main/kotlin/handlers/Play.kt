package handlers

import org.http4k.core.Headers
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.NOT_FOUND
import org.http4k.core.Status.Companion.OK
import result.map
import result.orElse
import storage.FileStorage
import storage.MetadataStorage
import java.util.UUID

object Play {
    operator fun invoke(
        request: Request,
        metadataStorage: MetadataStorage,
        fileStorage: FileStorage
    ): Response {
        val uuid = request.query("id") ?: return Response(BAD_REQUEST)
        val metadata = metadataStorage.find(UUID.fromString(uuid)) ?: return Response(NOT_FOUND)

        return fileStorage.stream(metadata.passwordProtectedLink).map { audioStream ->
            val headers: Headers = listOf(
                "Accept-Ranges" to "bytes",
                "Content-Length" to metadata.size.toString(),
                "Content-Range" to "bytes 0-${metadata.size - 1}/${metadata.size}"
            )
            Response(OK).body(audioStream).headers(headers)
        }.orElse { Response(NOT_FOUND) }
    }
}
