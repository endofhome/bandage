package scripts

import Bandage
import Bandage.StaticConfig.appName
import FfprobeInfo
import PreProcessMetadata.metadataReader
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import config.BandageConfig
import config.BandageConfigItem.DROPBOX_ACCESS_TOKEN
import config.BandageConfigItem.DROPBOX_LINK_PASSWORD
import config.Configuration
import config.ValidateConfig
import result.expectSuccess
import result.map
import result.orElse
import storage.DropboxFileStorage
import storage.FileStorage
import storage.HttpDropboxClient
import storage.MetadataStorage
import storage.PostgresMetadataStorage
import storage.toBitRate

// requires ffprobe (https://www.ffmpeg.org/)
fun updateBitrate(metadataStorage: MetadataStorage, fileStorage: FileStorage) {
    val ok = listOf(128000, 96000, 192000, 256000, 320000).map { it.toString() }

    return metadataStorage.tracks().map { all ->
        all.run {
            val numberOfFiles = this.size
            this.forEachIndexed { i, file ->
                if (ok.contains(file.bitRate?.value ?: "no bitrate")) {
                    println("skipping as bitrate is ${file.bitRate?.value}")
                    return@forEachIndexed
                }

                val tempFilePath = "file.tmp"
                val javaFile = fileStorage.downloadFile(file.path, tempFilePath).expectSuccess()
                val reader = metadataReader(tempFilePath)
                val fileInfoJsonString = reader.readLines().joinToString("")

                val ffprobeInfo: FfprobeInfo = jacksonObjectMapper().readValue(fileInfoJsonString)

                file.copy(
                    bitRate = ffprobeInfo.streams.firstOrNull()?.bit_rate?.toBitRate()
                        ?: throw RuntimeException("Couldn't get bitrate for ${file.uuid}")
                ).apply {
                    metadataStorage.updateTrack(this)
                    javaFile.delete()
                    println("Processed ${i + 1} / $numberOfFiles")
                }
            }
        }
    }.orElse { println(it.message) }
}

fun updatePasswordProtectedLinks(config: Configuration, metadataStorage: MetadataStorage, dropboxClient: HttpDropboxClient) =
    metadataStorage.tracks().map { all ->
        all.run {
            val numberOfFiles = this.size
            this.forEachIndexed { i, file ->
                val newLink = dropboxClient.createPasswordProtectedLink(file.path, config.get(DROPBOX_LINK_PASSWORD))
                    .expectSuccess()

                file.copy(
                    passwordProtectedLink = newLink
                ).apply {
                    metadataStorage.updateTrack(this)
                    println("Processed ${i + 1} / $numberOfFiles")
                }
            }
        }
    }.orElse { println(it.message) }

fun main() {
    val config = ValidateConfig(BandageConfig, Bandage.StaticConfig.configurationFilesDir)
    val dropboxClient = HttpDropboxClient(
        "$appName - seed-database-from-file-storage",
        config.get(DROPBOX_ACCESS_TOKEN)
    )

    updateBitrate(PostgresMetadataStorage(config), DropboxFileStorage(dropboxClient))
}
