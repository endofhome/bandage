package storage

import com.dropbox.core.DbxApiException
import com.dropbox.core.DbxDownloader
import com.dropbox.core.DbxException
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.DownloadErrorException
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.FolderMetadata
import com.dropbox.core.v2.files.Metadata
import com.dropbox.core.v2.files.WriteMode
import com.dropbox.core.v2.sharing.RequestedVisibility
import com.dropbox.core.v2.sharing.SharedLinkMetadata
import com.dropbox.core.v2.sharing.SharedLinkSettings
import org.http4k.client.ApacheClient
import org.http4k.core.BodyMode
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Uri
import result.Error
import result.Result
import result.Result.Failure
import result.asSuccess
import result.flatMap
import result.map
import result.orElse
import result.partition
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Date

class DropboxFileStorage(private val dropboxClient: SimpleDropboxClient) : FileStorage {
    override fun listFiles(): Result<Error, List<File>> =
        dropboxClient.listFolders().map { folders ->
            folders.flatMap { folder ->
                folder.files
            }
        }

    override fun downloadFile(remotePath: String, destinationPath: String): Result<Error, java.io.File> =
        dropboxClient.downloadFile(remotePath, destinationPath)

    override fun uploadFile(file: java.io.File, destinationPath: String): Result<Error, java.io.File> =
        dropboxClient.uploadFile(file, destinationPath)

    override fun stream(remotePath: String, fromByte: Long?): Result<Error, InputStream> =
        dropboxClient.streamFromPath(Paths.get(remotePath), fromByte)

    override fun publicLink(path: String, permission: FileStoragePermission): Result<Error, Uri> =
        when (permission) {
            is FileStoragePermission.PasswordProtected -> dropboxClient.createPasswordProtectedLink(path, permission.password)
        }
}

interface SimpleDropboxClient {
    fun listFolders(): Result<Error, List<Folder>>
    fun readTextFile(filename: String): Result<Error, List<String>>
    fun uploadFile(file: java.io.File, destinationPath: String): Result<Error, java.io.File>
    fun downloadFile(remotePath: String, destinationPath: String): Result<Error, java.io.File>
    fun streamFromPasswordProtectedUri(uri: Uri, skipBytes: Long? = null): Result<Error, InputStream>
    fun streamFromPath(path: Path, fromByte: Long? = null): Result<Error, InputStream>
    fun createPasswordProtectedLink(remotePathLower: String, password: String, expiryDate: Date? = null): Result<Error, Uri>
}

class HttpDropboxClient(identifier: String, private val accessToken: String) : SimpleDropboxClient {
    private val requestConfig: DbxRequestConfig = DbxRequestConfig.newBuilder(identifier).build()
    private val client: DbxClientV2 = DbxClientV2(requestConfig, accessToken)

    override fun listFolders(): Result<Error, List<Folder>> =
        filesRecursive().map { files ->
            val (foldersMetadata, filesMetadata) = files.partition { it is FolderMetadata }
            foldersMetadata.map { folderMetadata ->
                Folder(folderMetadata.name, filesMetadata.filter { fileMetadata ->
                    fileMetadata.pathDisplay.drop(1).takeWhile { char -> char != '/' } == folderMetadata.name
                }.map { fileMetadata -> File(fileMetadata.name, fileMetadata.pathLower) })
            }.asSuccess()
        }.orElse { Failure(it) }
    override fun createPasswordProtectedLink(remotePathLower: String, password: String, expiryDate: Date?): Result<Error, Uri> =
        revokeExistingSharedLinksFor(remotePathLower).map {
            client.sharing().createSharedLinkWithSettings(
                remotePathLower,
                SharedLinkSettings(RequestedVisibility.PASSWORD, password, expiryDate, null, null)
            ).url.toUri()
        }

    override fun streamFromPasswordProtectedUri(uri: Uri, skipBytes: Long?): Result<Error, InputStream> {
        val passwordProtectedLinkDownloader = client.sharing().getSharedLinkFile(uri.toString())

        return try {
            passwordProtectedLinkDownloader.inputStream.asSuccess()
        } catch (e: Exception) {
            Failure(Error("Downloader for $uri has already been closed"))
        }
    }

    override fun streamFromPath(path: Path, fromByte: Long?): Result<Error, InputStream> {
        val baseHeaders = listOf(
            "Authorization" to "Bearer $accessToken",
            "Dropbox-API-Arg" to "{\"path\": \"$path\"}"
        )
        val rangeHeaders = if (fromByte != null) listOf("Range" to "bytes=-$fromByte") else emptyList()
        val headers = baseHeaders + rangeHeaders

        val request = Request(Method.GET, "https://content.dropboxapi.com/2/files/download")
            .headers(headers)

        val response = ApacheClient(responseBodyMode = BodyMode.Stream)(request)

        return if (response.status.code in 200..299) {
            response.body.stream.asSuccess()
        } else {
            Failure(Error("Could not stream file $path"))
        }
    }

    override fun downloadFile(remotePath: String, destinationPath: String): Result<Error, java.io.File> =
        downloaderFor(remotePath).flatMap { it.inputStream(remotePath) }
            .map { inputStream ->
                java.io.File(destinationPath).apply {
                    this.createNewFile()
                    this.writeBytes(inputStream.readBytes())
                    inputStream.close()
                }
            }

    override fun uploadFile(file: java.io.File, destinationPath: String): Result<Error, java.io.File> {
        return try {
            ByteArrayInputStream(file.readBytes()).use { inputStream ->
                client.files().uploadBuilder(destinationPath)
                    .withMode(WriteMode.ADD)
                    .uploadAndFinish(inputStream)
            }
            file.asSuccess()
        } catch (e: Exception) {
            when (e) {
                is DbxApiException,
                is DbxException,
                is IOException      -> Failure(Error("Error writing file $destinationPath to Dropbox"))
                else                -> throw e
            }
        }
    }

    override fun readTextFile(filename: String): Result<Error, List<String>> =
        downloaderFor(filename).flatMap { downloader ->
            downloader.inputStream(filename)
        }.map { inputStream ->
            inputStream.reader().readLines().apply {
                inputStream.close()
            }
        }

    private fun filesRecursive(): Result<Error, List<Metadata>> =
        try {
            client.files()
                .listFolderBuilder("")
                .withRecursive(true)
                .start()
                .entries
                .toList()
                .asSuccess()
        } catch (e: Exception) {
            Failure(Error("Couldn't list files"))
        }

    private fun revokeExistingSharedLinksFor(pathLower: String): Result<Error, Unit> {
        val sharedLinksForFile: List<SharedLinkMetadata> =
            client.sharing().listSharedLinksBuilder().withPath(pathLower).start().links.toList()

        val results = sharedLinksForFile.map { link ->
            try {
                client.sharing().revokeSharedLink(link.url).asSuccess()
            } catch (e: Exception) {
                Failure(Error("Could not revoke link: ${link.url} for file path: $pathLower"))
            }
        }
        val (failures) = results.partition()
        return when {
            failures.isEmpty() -> Unit.asSuccess()
            else               -> Failure(Error(failures.joinToString { it.message }))
        }
    }

    private fun DbxDownloader<FileMetadata>.inputStream(filename: String): Result<Error, InputStream> =
        try {
            this.inputStream.asSuccess()
        } catch (e: Exception) {
            when (e) {
                is IllegalStateException -> Failure(Error("Error downloading file $filename"))
                else                     -> throw e
            }
        }

    private fun downloaderFor(filename: String): Result<Error, DbxDownloader<FileMetadata>> =
        try {
            val metadata = client.files().download(filename)
            metadata.asSuccess()
        } catch (e: Exception) {
            when (e) {
                is DownloadErrorException,
                is DbxException            -> Failure(Error("Error downloading file $filename"))
                else                       -> throw e
            }
        }
}
