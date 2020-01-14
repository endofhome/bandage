package scripts

import Bandage
import Bandage.StaticConfig.appName
import FfprobeInfo
import PreProcessMetadata.hashFile
import PreProcessMetadata.metadataReader
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import config.BandageConfig
import config.BandageConfigItem.DROPBOX_ACCESS_TOKEN
import config.BandageConfigItem.DROPBOX_LINK_PASSWORD
import config.ValidateConfig
import result.expectSuccess
import result.map
import result.orElse
import storage.AudioTrackMetadata
import storage.DropboxFileStorage
import storage.FileStoragePermission.PasswordProtected
import storage.HttpDropboxClient
import storage.LocalCsvMetadataStorage
import storage.MetadataStorage
import storage.toBitRate
import storage.toDuration
import java.time.Instant.EPOCH
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit
import java.util.UUID

// requires ffprobe (https://www.ffmpeg.org/)
private fun seedDatabase(metadataStorage: MetadataStorage) {
    val config = ValidateConfig(BandageConfig, Bandage.StaticConfig.configurationFilesDir)
    val dropboxClient = HttpDropboxClient(
        "$appName - seed-database-from-file-storage",
        config.get(DROPBOX_ACCESS_TOKEN)
    )
    val fileStorage = DropboxFileStorage(dropboxClient)

    fileStorage.listFiles().map { files ->
        files.mapIndexed { i, file ->
            val excludedFileTypes = listOf("txt", "csv", "zip")
            val allowedFileType = excludedFileTypes.none { file.name.toLowerCase().endsWith(it) }
            if (allowedFileType) {
                val tempFilePath = "file.tmp"
                val javaFile = fileStorage.downloadFile(file.path, tempFilePath).expectSuccess()
                val reader = metadataReader(tempFilePath)
                val fileInfoJsonString = reader.readLines().joinToString("")

                val ffprobeInfo: FfprobeInfo = jacksonObjectMapper().readValue(fileInfoJsonString)

                AudioTrackMetadata(
                    uuid = UUID.randomUUID(),
                    artist = ffprobeInfo.format.tags?.artist.orEmpty(),
                    album = ffprobeInfo.format.tags?.album.orEmpty(),
                    title = ffprobeInfo.format.tags?.title ?: file.name.replaceAfterLast(".", "").dropLast(1),
                    format = ffprobeInfo.format.format_name,
                    bitRate = ffprobeInfo.streams.firstOrNull()?.bit_rate?.toBitRate(),
                    duration = ffprobeInfo.format.duration?.toDuration(),
                    fileSize = ffprobeInfo.format.size.toInt(),
                    recordedDate = ffprobeInfo.format.tags?.date.orEmpty(),
                    recordedTimestamp = EPOCH.atZone(UTC),
                    recordedTimestampPrecision = ChronoUnit.FOREVER,
                    uploadedTimestamp = EPOCH.atZone(UTC),
                    passwordProtectedLink = fileStorage.publicLink(file.path, PasswordProtected(config.get(DROPBOX_LINK_PASSWORD))).expectSuccess(),
                    path = file.path,
                    hash = hashFile(javaFile.readBytes())
                ).apply {
                    javaFile.delete()
                }
            } else {
                null
            }.also {
                print("\rProcessed ${i + 1} / ${files.size}")
            }
        }.mapNotNull { it }.run {
            metadataStorage.addTracks(this)
        }
    }.orElse { throw RuntimeException(it.message) }
}

fun main() {
    seedDatabase(LocalCsvMetadataStorage)
}
