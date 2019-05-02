package storage

import result.Error
import result.Result
import result.Result.Failure
import result.asSuccess
import java.io.InputStream
import java.io.File as JavaFile

data class Folder(val name: String, val files: List<File>)
data class File(val name: String, val path: String)

interface FileStorage {
    fun listFiles(): Result<Error, List<File>>
    fun downloadFile(remotePath: String, destinationPath: String): Result<Error, JavaFile>
    fun publicLink(path: String, permission: FileStoragePermission): Result<Error, String>
    fun stream(url: String): Result<Error, InputStream>
}

sealed class FileStoragePermission {
    class PasswordProtected(val password: String): FileStoragePermission()
}

object DummyFileStorage : FileStorage {
    override fun downloadFile(remotePath: String, destinationPath: String): Result<Error, java.io.File> =
        TODO("not implemented")

    override fun publicLink(path: String, permission: FileStoragePermission): Result<Error, String> =
        TODO("not implemented")

    override fun stream(url: String): Result<Error, InputStream> =
        InputStream.nullInputStream().asSuccess()

    override fun listFiles(): Result<Error, List<File>> =
        TODO("not implemented")
}

typealias FileUrl = String
typealias FileStringData = String
class StubFileStorage(private val files: Map<FileUrl, FileStringData>) : FileStorage {
    override fun listFiles(): Result<Error, List<File>> =
        files.entries.map { File(name = it.value, path = it.key) }.asSuccess()

    override fun downloadFile(remotePath: String, destinationPath: String): Result<Error, java.io.File> = TODO("not implemented")

    override fun publicLink(path: String, permission: FileStoragePermission): Result<Error, String> = TODO("not implemented")

    override fun stream(url: String): Result<Error, InputStream> {
        val fileStringData = files[url] ?: return Failure(Error("File not found at path $url"))
        return fileStringData.toByteArray().inputStream().asSuccess()
    }

}
