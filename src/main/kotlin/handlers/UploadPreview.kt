package handlers

import AuthenticatedRequest
import org.http4k.core.MultipartFormBody
import org.http4k.core.Response
import org.http4k.core.Status.Companion.BAD_REQUEST
import org.http4k.core.Status.Companion.OK

object UploadPreview {
    operator fun invoke(authenticatedRequest: AuthenticatedRequest): Response {
        try {
            MultipartFormBody.from(authenticatedRequest.request)
        } catch (e: Exception) {
            return Response(BAD_REQUEST)
        }

        return Response(OK)
    }
}
