package storage

import result.map
import result.orElse
import java.io.FileReader
import java.io.FileWriter
import java.util.UUID

data class AudioFileMetadata(
    val uuid: UUID,
    val artist: String,
    val album: String,
    val title: String,
    val format: String,
    val bitRate: String,
    val duration: Duration?,
    val size: Int,
    val recordedDate: String,
    val passwordProtectedLink: String,
    val path: String,
    val hash: String
)

class Duration(val value: String)

fun String.toDuration() = Duration(this)

interface MetadataStorage {
    fun all(): List<AudioFileMetadata>
    fun find(uuid: UUID): AudioFileMetadata?
    fun write(newMetadata: List<AudioFileMetadata>)
}

class DropboxCsvMetadataStorage(dropboxClient: SimpleDropboxClient) : MetadataStorage {
    private val filePath = "/seed-data.csv"
    private val lineSeparator = "\n"
    private val headerLine =
        "ID,Artist,Album,Title,Format,Bitrate,Duration,Size,Recorded date,Password protected link,Path,SHA-256$lineSeparator"

    private val store = dropboxClient.readTextFile(filePath).map { lines ->
        lines.dropHeader().map { line ->
            line.split(",").run {
                AudioFileMetadata(
                    UUID.fromString(this[0]),
                    this[1],
                    this[2],
                    this[3],
                    this[4],
                    this[5],
                    this[6].toDuration(),
                    this[7].toInt(),
                    this[8],
                    this[9],
                    this[10],
                    this[11]
                )
            }
        }
    }.orElse { throw Exception("Couldn't read file $filePath in Dropbox") }

    override fun all(): List<AudioFileMetadata> = store

    override fun find(uuid: UUID): AudioFileMetadata? =
        store.find { audioFileMetadata -> audioFileMetadata.uuid == uuid }

    override fun write(newMetadata: List<AudioFileMetadata>) = TODO("not yet implemented")

    private fun List<String>.dropHeader() = if (this[0] == headerLine.removeSuffix(lineSeparator)) drop(1) else this
}

object LocalCsvMetadataStorage : MetadataStorage {
    private const val flatFileName = "seed-data.csv"
    private val fileWriter = FileWriter(flatFileName, true)
    private val lineSeparator = System.lineSeparator()
    private val headerLine =
        "ID,Artist,Album,Title,Format,Bitrate,Duration,Size,Recorded date,Password protected link,Path,SHA-256$lineSeparator"

    private val store = FileReader(flatFileName).readLines().dropHeader().map { line ->
        line.split(",").run {
            AudioFileMetadata(
                UUID.fromString(this[0]),
                this[1],
                this[2],
                this[3],
                this[4],
                this[5],
                this[6].toDuration(),
                this[7].toInt(),
                this[8],
                this[9],
                this[10],
                this[11]
            )
        }
    }

    override fun all(): List<AudioFileMetadata> = store

    override fun find(uuid: UUID): AudioFileMetadata? =
        store.find { audioFileMetadata -> audioFileMetadata.uuid == uuid }

    override fun write(newMetadata: List<AudioFileMetadata>) {
        fileWriter.append(headerLine)

        newMetadata.forEach { singleFileMetadata ->
            fileWriter.append(singleFileMetadata.run {
                "$uuid,$artist,$album,$title,$format,$bitRate,$duration,$size,$recordedDate,$passwordProtectedLink,$path,$hash$lineSeparator"
            })
        }

        fileWriter.flush()
        fileWriter.close()
    }

    private fun List<String>.dropHeader() = if (this[0] == headerLine.removeSuffix(lineSeparator)) drop(1) else this
}
