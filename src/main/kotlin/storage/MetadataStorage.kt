package storage

import RouteMappings.play
import http.HttpConfig.environment
import org.http4k.core.Uri
import result.Error
import result.Result
import result.Result.Failure
import result.asSuccess
import result.map
import result.orElse
import storage.Collection.ExistingCollection
import storage.Collection.NewCollection
import storage.HasPreferredTitle.TitleType.TITLE
import storage.HasPreferredTitle.TitleType.WORKING_TITLE
import java.io.FileReader
import java.io.FileWriter
import java.math.BigDecimal
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

data class AudioTrackMetadata(
    val uuid: UUID,
    val artist: String,
    val album: String,
    override val title: String = "untitled",
    override val workingTitles: List<String> = emptyList(),
    val format: String,
    val bitRate: BitRate?,
    val duration: Duration?,
    val fileSize: Int,
    val normalisedFileSize: Long? = null,
    val recordedDate: String,
    val recordedTimestamp: ZonedDateTime,
    val recordedTimestampPrecision: ChronoUnit,
    val uploadedTimestamp: ZonedDateTime,
    val passwordProtectedLink: Uri,
    val path: String,
    val hash: String,
    val collections: List<ExistingCollection> = emptyList()
) : HasPreferredTitle, HasPresentationFormat {
    val playUrl: Uri = "${environment.config.baseUrl}$play/$uuid".toUri()
    val downloadUrl: Uri = "${environment.config.baseUrl}$play/$uuid".toUri()
}

interface HasPreferredTitle {
    val title: String
    val workingTitles: List<String>

    fun preferredTitle(): Pair<String, TitleType> {
        val undesirableTitle = title == "untitled" || title.isEmpty() || title.isBlank()

        return if (undesirableTitle && workingTitles.isNotEmpty()) {
            workingTitles.first() to WORKING_TITLE
        } else {
            title to TITLE
        }
    }

    enum class TitleType(val key: String) {
        TITLE("title"),
        WORKING_TITLE("working-title")
    }
}

interface HasPresentationFormat {
    companion object {
        fun BitRate.presentationFormat(): String = (this.value.toBigDecimal() / BigDecimal(1000)).toString() + " kbps"

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

sealed class Collection {
    data class NewCollection(val title: String): Collection()
    data class ExistingCollection(val uuid: UUID, val title: String, val tracks: Set<UUID>): Collection() // TODO tracks should be AudioTrackMetadata
}

data class BitRate(val value: String)
data class Duration(val value: String)

fun String.toBitRate() = BitRate(this)
fun String.toDuration() = Duration(this)
fun String.toZonedDateTime() = ZonedDateTime.parse(this)
    ?: throw RuntimeException("Could not parse $this as ZonedDateTime")
fun String.toChronoUnit() = ChronoUnit.valueOf(this)
fun String.toUri() = Uri.of(this)

interface MetadataStorage {
    fun tracks(): Result<Error, List<AudioTrackMetadata>>
    fun findTrack(uuid: UUID): Result<Error, AudioTrackMetadata?>
    fun addTracks(newMetadata: List<AudioTrackMetadata>)
    fun updateTrack(updatedMetadata: AudioTrackMetadata): Result<Error, AudioTrackMetadata>
    fun addExistingTrackToCollection(existingTrack: AudioTrackMetadata, collection: Collection)
    fun findCollection(uuid: UUID): Result<Error, ExistingCollection?>
    fun addCollection(newCollection: NewCollection, firstElement: AudioTrackMetadata): ExistingCollection
    fun updateCollection(updatedCollection: ExistingCollection): Result<Error, ExistingCollection>
}

class DropboxCsvMetadataStorage(dropboxClient: SimpleDropboxClient) : MetadataStorage {
    private val filePath = "/seed-data.csv"

    private val lineSeparator = "\n"
    private val headerLine =
        "ID,Artist,Album,Title,Format,Bitrate,Duration,Size,Recorded date,Recorded timestamp,Recorded timestamp precision,Uploaded date,Password protected link,Path,SHA-256,Collections$lineSeparator"
    private val store = dropboxClient.readTextFile(filePath).map { lines ->
        lines.dropHeader().map { line ->
            line.split(",").run {
                AudioTrackMetadata(
                    UUID.fromString(this[0]),
                    this[1],
                    this[2],
                    this[3],
                    emptyList(),
                    this[4],
                    this[5].toBitRate(),
                    this[6].toDuration(),
                    this[7].toInt(),
                    this[8].toLong(),
                    this[9],
                    this[10].toZonedDateTime(),
                    this[11].toChronoUnit(),
                    this[12].toZonedDateTime(),
                    this[13].toUri(),
                    this[14],
                    this[15],
                    this[16].split('\t').map { ExistingCollection(UUID.fromString(it), "dummy - should get this from another file", emptySet()) }
                )
            }
        }.asSuccess()
    }.orElse { Failure(Error("Couldn't read file $filePath in Dropbox")) }

    override fun tracks(): Result<Error, List<AudioTrackMetadata>> = store

    override fun findTrack(uuid: UUID): Result<Error, AudioTrackMetadata?> =
        store.map { it.find { audioFileMetadata -> audioFileMetadata.uuid == uuid } }

    override fun addTracks(newMetadata: List<AudioTrackMetadata>) = error("not yet implemented")
    override fun updateTrack(updatedMetadata: AudioTrackMetadata) = error("not yet implemented")
    override fun addExistingTrackToCollection(existingTrack: AudioTrackMetadata, collection: Collection) =
        error("not yet implemented")
    override fun findCollection(uuid: UUID) = error("not yet implemented")
    override fun updateCollection(updatedCollection: ExistingCollection) = error("not yet implemented")
    override fun addCollection(newCollection: NewCollection, firstElement: AudioTrackMetadata) =
        error("not yet implemented")

    private fun List<String>.dropHeader() = if (this[0] == headerLine.removeSuffix(lineSeparator)) drop(1) else this
}

object LocalCsvMetadataStorage : MetadataStorage {
    private const val flatFileName = "bitrate-experiment.csv"
    private val fileWriter = FileWriter(flatFileName, true)
    private val lineSeparator = System.lineSeparator()
    private val headerLine =
        "ID,Artist,Album,Title,Format,Bitrate,Duration,Size,Recorded date,Password protected link,Path,SHA-256$lineSeparator"

    private val store =
        try {
            FileReader(flatFileName).readLines().dropHeader().map { line ->
                line.split(",").run {
                    AudioTrackMetadata(
                        UUID.fromString(this[0]),
                        this[1],
                        this[2],
                        this[3],
                        emptyList(),
                        this[4],
                        this[5].toBitRate(),
                        this[6].toDuration(),
                        this[7].toInt(),
                        this[8].toLong(),
                        this[9],
                        this[10].toZonedDateTime(),
                        this[11].toChronoUnit(),
                        this[12].toZonedDateTime(),
                        this[13].toUri(),
                        this[14],
                        this[15],
                        this[16].split('\t').map { ExistingCollection(UUID.fromString(it), "dummy - should get this from another file", emptySet()) }
                    )
                }
            }.asSuccess()
        } catch (e: Exception) {
            Failure(Error("Couldn't read file $flatFileName"))
        }

    override fun tracks(): Result<Error, List<AudioTrackMetadata>> = store

    override fun findTrack(uuid: UUID): Result<Error, AudioTrackMetadata?> =
        store.map { it.find { audioFileMetadata -> audioFileMetadata.uuid == uuid } }

    override fun addTracks(newMetadata: List<AudioTrackMetadata>) {
        fileWriter.append(headerLine)

        newMetadata.forEach { singleFileMetadata ->
            fileWriter.append(singleFileMetadata.run {
                "$uuid,$artist,$album,$title,$format,$bitRate,$duration,$fileSize,$recordedDate,$recordedTimestamp,$recordedTimestampPrecision,$uploadedTimestamp,$passwordProtectedLink,$path,$hash$collections,$lineSeparator"
            })
        }

        fileWriter.flush()
        fileWriter.close()
    }

    override fun updateTrack(updatedMetadata: AudioTrackMetadata) = error("not implemented")
    override fun addExistingTrackToCollection(existingTrack: AudioTrackMetadata, collection: Collection) =
        error("not yet implemented")
    override fun findCollection(uuid: UUID) = error("not implemented")
    override fun updateCollection(updatedCollection: ExistingCollection) = error("not yet implemented")
    override fun addCollection(newCollection: NewCollection, firstElement: AudioTrackMetadata) =
        error("not yet implemented")

    private fun List<String>.dropHeader() = if (this[0] == headerLine.removeSuffix(lineSeparator)) drop(1) else this
}
