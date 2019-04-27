package storage

import com.dropbox.core.DbxDownloader
import com.dropbox.core.DbxException
import com.dropbox.core.DbxHost
import com.dropbox.core.DbxRequestConfig
import com.dropbox.core.http.HttpRequestor
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.DbxRawClientV2
import com.dropbox.core.v2.common.PathRoot
import com.dropbox.core.v2.files.DownloadErrorException
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.FolderMetadata
import com.dropbox.core.v2.files.Metadata
import com.dropbox.core.v2.sharing.DbxUserSharingRequests
import com.dropbox.core.v2.sharing.RequestedVisibility
import com.dropbox.core.v2.sharing.SharedLinkMetadata
import com.dropbox.core.v2.sharing.SharedLinkSettings
import result.Error
import result.Result
import result.Result.Failure
import result.asSuccess
import result.flatMap
import result.map
import result.orElse
import result.partition
import java.io.InputStream
import java.util.*

class DropboxFileStorage(private val dropboxClient: SimpleDropboxClient) : FileStorage {
    override fun listFiles(): Result<Error, List<File>> =
        dropboxClient.listFolders().map { folders ->
            folders.flatMap { folder ->
                folder.files
            }
        }

    override fun downloadFile(remotePath: String, destinationPath: String): Result<Error, java.io.File> =
        dropboxClient.downloadFile(remotePath, destinationPath)

    override fun stream(url: String): Result<Error, InputStream> =
        dropboxClient.streamFromPasswordProtectedUrl(url)

    override fun publicLink(path: String, permission: FileStoragePermission): Result<Error, String> =
        when (permission) {
            is FileStoragePermission.PasswordProtected -> dropboxClient.createPasswordProtectedLink(path, permission.password)
        }
}

interface SimpleDropboxClient {
    fun listFolders(): Result<Error, List<Folder>>
    fun readTextFile(filename: String): Result<Error, List<String>>
    fun downloadFile(remotePath: String, destinationPath: String): Result<Error, java.io.File>
    fun streamFromPasswordProtectedUrl(url: String): Result<Error, InputStream>
    fun createPasswordProtectedLink(remotePathLower: String, password: String, expiryDate: Date? = null): Result<Error, String>
}

class HttpDropboxClient(identifier: String, accessToken: String) : SimpleDropboxClient {
    private val requestConfig: DbxRequestConfig = DbxRequestConfig.newBuilder(identifier).build()
    private val client: DbxClientV2 = DbxClientV2(requestConfig, accessToken)
    private val rawClient: DbxRawClientV2 = object : DbxRawClientV2(requestConfig, DbxHost.DEFAULT, null, null) {
        override fun withPathRoot(pathRoot: PathRoot): DbxRawClientV2 = TODO("not implemented")

        override fun addAuthHeaders(headers: MutableList<HttpRequestor.Header>) {
            headers.add(HttpRequestor.Header("Authorization", "Bearer $accessToken"))
        }
    }

    override fun listFolders(): Result<Error, List<Folder>> =
        filesRecursive().map { files ->
            val (foldersMetadata, filesMetadata) = files.partition { it is FolderMetadata }
            foldersMetadata.map { folderMetadata ->
                Folder(folderMetadata.name, filesMetadata.filter { fileMetadata ->
                    fileMetadata.pathDisplay.drop(1).takeWhile { char -> char != '/' } == folderMetadata.name
                }.map { fileMetadata -> File(fileMetadata.name, fileMetadata.pathLower) })
            }.asSuccess()
        }.orElse { Failure(it) }

    override fun createPasswordProtectedLink(remotePathLower: String, password: String, expiryDate: Date?): Result<Error, String> =
        revokeExistingSharedLinksFor(remotePathLower).map {
            DbxUserSharingRequests(rawClient).createSharedLinkWithSettings(
                remotePathLower,
                SharedLinkSettings(RequestedVisibility.PASSWORD, password, expiryDate)
            ).url
        }

    override fun streamFromPasswordProtectedUrl(url: String): Result<Error, InputStream> {
        val passwordProtectedLinkDownloader = DbxUserSharingRequests(rawClient).getSharedLinkFile(url)

        return try {
            passwordProtectedLinkDownloader.inputStream.asSuccess()
        } catch (e: Exception) {
            Failure(Error("Downloader for $url has already been closed"))
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
            DbxUserSharingRequests(rawClient).listSharedLinksBuilder().withPath(pathLower).start().links.toList()

        val results = sharedLinksForFile.map { link ->
            try {
                DbxUserSharingRequests(rawClient).revokeSharedLink(link.url).asSuccess()
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
