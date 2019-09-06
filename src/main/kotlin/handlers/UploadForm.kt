package handlers

import AuthenticatedRequest
import Bandage
import User
import org.http4k.core.Response
import org.http4k.core.Status.Companion.OK
import org.http4k.core.with
import org.http4k.template.ViewModel

object UploadForm {
    operator fun invoke(authenticatedRequest: AuthenticatedRequest): Response {
        val unsupportedFileType = authenticatedRequest.request.query("unsupported-file-type")
        return Response(OK).with(Bandage.StaticConfig.view of UploadPage(authenticatedRequest.user, unsupportedFileType))
    }
}

data class UploadPage(val loggedInUser: User, val unsupportedFileType: String?) : ViewModel {
    override fun template() = "upload_track"
}