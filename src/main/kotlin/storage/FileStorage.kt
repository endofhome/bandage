package storage

import org.http4k.core.Uri
import result.Error
import result.Result
import java.io.InputStream
import java.io.File as JavaFile

data class Folder(val name: String, val files: List<File>)
data class File(val name: String, val path: String)

interface FileStorage {
    fun listFiles(): Result<Error, List<File>>
    fun downloadFile(remotePath: String, destinationPath: String): Result<Error, JavaFile>
    fun uploadFile(file: java.io.File, destinationPath: String): Result<Error, JavaFile>
    fun publicLink(path: String, permission: FileStoragePermission): Result<Error, Uri>
    fun stream(uri: Uri): Result<Error, InputStream>
}

sealed class FileStoragePermission {
    class PasswordProtected(val password: String): FileStoragePermission()
}
