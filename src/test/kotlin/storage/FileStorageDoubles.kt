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

    override fun stream(remotePath: String, fromByte: Long?): Result<Error, InputStream> =
        InputStream.nullInputStream().asSuccess()

    override fun listFiles(): Result<Error, List<storage.File>> =
        error("not implemented")
}

class StubFileStorage(private val files: MutableMap<String, ByteArray>) : DummyFileStorage() {
    override fun listFiles(): Result<Error, List<storage.File>> =
        files.entries.map { File(name = it.key, path = it.key) }.asSuccess()

    override fun stream(remotePath: String, fromByte: Long?): Result<Error, InputStream> {
        val fileContents = files[remotePath] ?: return Result.Failure(Error("File not found at path $remotePath"))
        return fileContents.inputStream().asSuccess()
    }

    override fun uploadFile(file: File, destinationPath: String): Result<Error, File> {
        files[Uri.of(destinationPath).toString()] = file.readBytes()
        return file.asSuccess()
    }

    override fun publicLink(path: String, permission: FileStoragePermission): Result<Error, Uri> =
        Uri.of("some-public-link").asSuccess()
}

class StubFileStorageFactory(private val files: MutableMap<String, ByteArray>): DummyFileStorageFactory() {
    override fun invoke(config: Configuration): FileStorage = StubFileStorage(files)
}

open class DummyFileStorageFactory: FileStorageFactory {
    override operator fun invoke(config: Configuration): FileStorage = DummyFileStorage()
}
