package storage

import config.Configuration
import org.http4k.core.Uri
import result.Error
import result.Result
import result.asSuccess
import java.io.File
import java.io.InputStream

open class DummyFileStorage : FileStorage {
    override fun downloadFile(remotePath: String, destinationPath: String): Result<Error, File> =
        error("not implemented")

    override fun uploadFile(file: File, destinationPath: String): Result<Error, File> =
        error("not implemented")

    override fun publicLink(path: String, permission: FileStoragePermission): Result<Error, Uri> =
        error("not implemented")

    override fun stream(uri: Uri): Result<Error, InputStream> =
        InputStream.nullInputStream().asSuccess()

    override fun listFiles(): Result<Error, List<storage.File>> =
        error("not implemented")
}

typealias FileStringData = String
class StubFileStorage(private val files: Map<Uri, FileStringData>) : DummyFileStorage() {
    override fun listFiles(): Result<Error, List<storage.File>> =
        files.entries.map { File(name = it.value, path = it.key.toString()) }.asSuccess()

    override fun stream(uri: Uri): Result<Error, InputStream> {
        val fileStringData = files[uri] ?: return Result.Failure(Error("File not found at path $uri"))
        return fileStringData.toByteArray().inputStream().asSuccess()
    }
}

class StubFileStorageFactory(private val files: Map<Uri, FileStringData>): DummyFileStorageFactory() {
    override fun invoke(config: Configuration): FileStorage = StubFileStorage(files)
}

open class DummyFileStorageFactory: FileStorageFactory {
    override operator fun invoke(config: Configuration): FileStorage = DummyFileStorage()
}
