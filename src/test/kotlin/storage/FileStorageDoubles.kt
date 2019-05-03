package storage

import config.Configuration
import result.Error
import result.Result
import result.asSuccess
import java.io.File
import java.io.InputStream

open class DummyFileStorage : FileStorage {
    override fun downloadFile(remotePath: String, destinationPath: String): Result<Error, File> =
        TODO("not implemented")

    override fun publicLink(path: String, permission: FileStoragePermission): Result<Error, String> =
        TODO("not implemented")

    override fun stream(url: String): Result<Error, InputStream> =
        InputStream.nullInputStream().asSuccess()

    override fun listFiles(): Result<Error, List<storage.File>> =
        TODO("not implemented")
}

typealias FileUrl = String
typealias FileStringData = String
class StubFileStorage(private val files: Map<FileUrl, FileStringData>) : DummyFileStorage() {
    override fun listFiles(): Result<Error, List<storage.File>> =
        files.entries.map { File(name = it.value, path = it.key) }.asSuccess()

    override fun stream(url: String): Result<Error, InputStream> {
        val fileStringData = files[url] ?: return Result.Failure(Error("File not found at path $url"))
        return fileStringData.toByteArray().inputStream().asSuccess()
    }
}

class StubFileStorageFactory(private val files: Map<FileUrl, FileStringData>): DummyFileStorageFactory() {
    override fun invoke(config: Configuration): FileStorage = StubFileStorage(files)
}

open class DummyFileStorageFactory: FileStorageFactory {
    override operator fun invoke(config: Configuration): FileStorage = DummyFileStorage()
}
