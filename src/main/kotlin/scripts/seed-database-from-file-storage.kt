package scripts

import Bandage
import Bandage.StaticConfig.appName
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import config.BandageConfig
import config.BandageConfigItem.DROPBOX_ACCESS_TOKEN
import config.BandageConfigItem.DROPBOX_LINK_PASSWORD
import config.ValidateConfig
import result.expectSuccess
import result.map
import result.orElse
import storage.AudioFileMetadata
import storage.DropboxFileStorage
import storage.FileStoragePermission.PasswordProtected
import storage.HttpDropboxClient
import storage.LocalCsvMetadataStorage
import storage.MetadataStorage
import storage.toDuration
import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.*


fun seedDatabase(metadataStorage: MetadataStorage) {
    val config = ValidateConfig(BandageConfig, Bandage.StaticConfig.configurationFilesDir)
    val dropboxClient = HttpDropboxClient(
        "$appName - seed-database-from-file-storage",
        config.get(DROPBOX_ACCESS_TOKEN)
    )
    val fileStorage = DropboxFileStorage(dropboxClient)

    fileStorage.listFiles().map { files ->
        files.map { file ->
            val excludedFileTypes = listOf("txt", "csv", "zip")
            val allowedFileType = excludedFileTypes.none { file.name.toLowerCase().endsWith(it) }
            if (allowedFileType) {
                val tempFilePath = "file.tmp"
                val javaFile = fileStorage.downloadFile(file.path, tempFilePath).expectSuccess()
                val reader = metadataReader(tempFilePath)
                val fileInfoJsonString = reader.readLines().joinToString("")

                val ffprobeInfo: FfprobeInfo = jacksonObjectMapper().readValue(fileInfoJsonString)

                AudioFileMetadata(
                    uuid = UUID.randomUUID(),
                    artist = ffprobeInfo.format.tags?.artist.orEmpty(),
                    album = ffprobeInfo.format.tags?.album.orEmpty(),
                    title = ffprobeInfo.format.tags?.title ?: file.name.replaceAfterLast(".", "").dropLast(1),
                    format = ffprobeInfo.format.format_name,
                    bitRate = ffprobeInfo.format.bit_rate,
                    duration = ffprobeInfo.format.duration?.toDuration(),
                    fileSize = ffprobeInfo.format.size.toInt(),
                    recordedDate = ffprobeInfo.format.tags?.date.orEmpty(),
                    passwordProtectedLink = fileStorage.publicLink(file.path, PasswordProtected(config.get(DROPBOX_LINK_PASSWORD))).expectSuccess(),
                    path = file.path,
                    hash = hashFile(javaFile.readBytes())
                ).apply {
                    javaFile.delete()
                }
            } else {
                null
            }
        }.mapNotNull { it }.run {
            metadataStorage.write(this)
        }
    }.orElse { throw RuntimeException(it.message) }
}

private fun metadataReader(tempFileName: String): BufferedReader {
    val ffprobeMetadata =
        "ffprobe -v quiet -print_format json -show_format $tempFileName".split(" ").toMutableList()
    val process = ProcessBuilder().command(ffprobeMetadata).start()
    return BufferedReader(InputStreamReader(process.inputStream))
}

private fun hashFile(file: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashAsBytes = digest.digest(file)

    return hashAsBytes.map { byte ->
        String.format("%02x", byte)
    }.joinToString("")
}

data class FfprobeInfo(
    val format: Format
)

data class Format(
    val filename: String,
    val nb_streams: Int,
    val nb_programs: Int,
    val format_long_name: String,
    val start_time: String?,
    val probe_score: Int,
    val format_name: String,
    val duration: String?,
    val size: String,
    val bit_rate: String,
    val tags: Tags?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Tags(
    val genre: String?,
    val comment: String?,
    val artist: String?,
    val album: String?,
    val title: String?,
    val date: String?
)

fun main() {
    seedDatabase(LocalCsvMetadataStorage)
}
