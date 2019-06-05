package storage

import RouteMappings.play
import http.HttpConfig.environment
import org.http4k.core.Uri
import result.map
import result.orElse
import java.io.FileReader
import java.io.FileWriter
import java.math.BigDecimal
import java.util.UUID

// TODO If this isn't about a file, rename it
data class AudioFileMetadata(
    val uuid: UUID,
    val artist: String,
    val album: String,
    val title: String,
    val format: String,
    val bitRate: BitRate?,
    val duration: Duration?,
    val size: Int,
    val recordedDate: String,
    val passwordProtectedLink: String,
    val path: String,
    val hash: String,
    val collections: List<UUID> = emptyList()
) {
    val playUrl: Uri = Uri.of("${environment.config.baseUrl}$play/$uuid")

    companion object {
        fun BitRate.presentationFormat(): String = (this.value.toBigDecimal() / BigDecimal(1000)).toString()

        fun Duration.presentationFormat(): String {
            val (rawSeconds, _) = this.value.split(".")
            val duration = java.time.Duration.ofSeconds(rawSeconds.toLong())
            val hours = duration.toHoursPart().toString().emptyIfZero()
            val minutes = duration.toMinutesPart()
            val seconds = duration.toSecondsPart().toString().padStart(2, '0')
            return "$hours$minutes:$seconds"
        }

        private fun String.emptyIfZero(): String =
            if (this != "0") "$this:"
            else ""
    }
}

class BitRate(val value: String)
class Duration(val value: String)

fun String.toBitRate() = BitRate(this)
fun String.toDuration() = Duration(this)

// TODO return a result
interface MetadataStorage {
    fun all(): List<AudioFileMetadata>
    fun find(uuid: UUID): AudioFileMetadata?
    fun write(newMetadata: List<AudioFileMetadata>)
    fun update(updatedMetadata: AudioFileMetadata)
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
                    this[5].toBitRate(),
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

    override fun update(updatedMetadata: AudioFileMetadata) = TODO("not yet implemented")

    private fun List<String>.dropHeader() = if (this[0] == headerLine.removeSuffix(lineSeparator)) drop(1) else this
}

object LocalCsvMetadataStorage : MetadataStorage {
    private const val flatFileName = "bitrate-experiment.csv"
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
                this[5].toBitRate(),
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

    override fun update(updatedMetadata: AudioFileMetadata) = TODO("not implemented")

    private fun List<String>.dropHeader() = if (this[0] == headerLine.removeSuffix(lineSeparator)) drop(1) else this
}
