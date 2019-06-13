package storage

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import config.BandageConfigItem.METADATA_DB_HOST
import config.BandageConfigItem.METADATA_DB_NAME
import config.BandageConfigItem.METADATA_DB_PASSWORD
import config.BandageConfigItem.METADATA_DB_PORT
import config.BandageConfigItem.METADATA_DB_SSL_MODE
import config.BandageConfigItem.METADATA_DB_USER
import config.Configuration
import org.postgresql.ds.PGSimpleDataSource
import result.Result
import result.Result.Failure
import result.asSuccess
import result.flatMap
import result.map
import result.orElse
import storage.Collection.ExistingCollection
import java.sql.ResultSet
import java.util.UUID

class PostgresMetadataStorage(config: Configuration) : MetadataStorage {
    private val datasource = PGSimpleDataSource().apply {
        serverName = config.get(METADATA_DB_HOST)
        portNumber = config.get(METADATA_DB_PORT).toInt()
        databaseName = config.get(METADATA_DB_NAME)
        user = config.get(METADATA_DB_USER)
        password = config.get(METADATA_DB_PASSWORD)
        sslMode = config.get(METADATA_DB_SSL_MODE)
    }
    private val connection = datasource.connection

    override fun tracks(): Result<Error, List<AudioTrackMetadata>> =
        try {
            connection.prepareStatement("SELECT * FROM public.tracks").use { statement ->
                statement.executeQuery().use { resultSet ->
                    generateSequence {
                        if (resultSet.next()) resultSet.toAudioFileMetadata() else null
                    }.toList()
                }
            }.asSuccess()
        } catch (e: Exception) {
            Failure(Error("Error reading all tracks metadata"))
        }

    override fun findTrack(uuid: UUID): Result<Error, AudioTrackMetadata?> =
        try {
            connection.prepareStatement("SELECT * FROM public.tracks WHERE id = '$uuid'").use { statement ->
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) resultSet.toAudioFileMetadata() else null
                }.asSuccess()
            }
        } catch (e: Exception) {
            Failure(Error("Error finding track $uuid in metadata storage"))

        }

    override fun addTracks(newMetadata: List<AudioTrackMetadata>) {
        val preparedStatement = connection.prepareStatement("""
                INSERT INTO tracks VALUES ${newMetadata.joinToString(",") { "(?::uuid, ?::jsonb)" }};
            """.trimIndent())

        val uuidIndexes = 1..newMetadata.size * 2 step 2

        newMetadata.zip(uuidIndexes).forEach { (audioFileMetadata, uuidIndex) ->
            preparedStatement.setString(uuidIndex, audioFileMetadata.uuid.toString())
            preparedStatement.setString(uuidIndex + 1, audioFileMetadata.toJsonString())
        }

        preparedStatement.use { statement ->
            statement.executeUpdate()
        }
    }

    override fun updateTrack(updatedMetadata: AudioTrackMetadata): Result<Error, AudioTrackMetadata> =
        findTrack(updatedMetadata.uuid).flatMap { track ->
            if (track != null) {
                val preparedStatement = connection.prepareStatement("""
                    UPDATE tracks SET metadata = ?::jsonb WHERE id = '${updatedMetadata.uuid}';
                """.trimIndent())

                preparedStatement.setString(1, updatedMetadata.toJsonString())

                preparedStatement.use { statement ->
                    statement.executeUpdate()
                }
                updatedMetadata.asSuccess()
            } else {
                Failure(Error("Couldn't find track ${updatedMetadata.uuid} to update"))
            }
        }

    override fun findCollection(uuid: UUID): Result<Error, ExistingCollection?> =
        try {
            connection.prepareStatement("SELECT * FROM public.collections WHERE id = '$uuid'").use { statement ->
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) resultSet.toCollection() else null
                }.asSuccess()
            }
        } catch (e: Exception) {
            Failure(Error("Error while finding collection $uuid in metadata storage:\n${e.message}"))
        }

    // TODO this is a bit weird, as it adds the firstElement to the collection, but not the collection to the first element
    override fun addCollection(newCollection: Collection.NewCollection, firstElement: AudioTrackMetadata): ExistingCollection {
        val upgradedCollection = ExistingCollection(UUID.randomUUID(), newCollection.title, setOf(firstElement.uuid))
        val preparedStatement = connection.prepareStatement("""
            INSERT INTO collections VALUES (?::uuid, ?, ?::uuid[]);
        """.trimIndent()
        )

        preparedStatement.setString(1, upgradedCollection.uuid.toString())
        preparedStatement.setString(2, upgradedCollection.title)
        preparedStatement.setArray(3, connection.createArrayOf("UUID", upgradedCollection.tracks.toTypedArray()))

        preparedStatement.use { statement ->
            statement.executeUpdate()
        }

        return upgradedCollection
    }

    override fun updateCollection(updatedCollection: ExistingCollection): Result<Error, ExistingCollection> =
        findCollection(updatedCollection.uuid).flatMap { collection ->
            if (collection != null) {
                val preparedStatement = connection.prepareStatement("""
                    UPDATE collections SET tracks = ?::uuid[] WHERE id = '${updatedCollection.uuid}';
                """.trimIndent())

                preparedStatement.setArray(1, connection.createArrayOf("UUID", updatedCollection.tracks.toTypedArray()))

                preparedStatement.use { statement ->
                    statement.executeUpdate()
                }
                updatedCollection.asSuccess()
            } else {
                Failure(Error("Couldn't find collection ${updatedCollection.uuid} to update"))
            }
        }

    override fun addExistingTrackToCollection(existingTrack: AudioTrackMetadata, collection: Collection) =
        when (collection) {
            is ExistingCollection -> findCollection(collection.uuid).map { foundCollection ->
                if (foundCollection != null) {
                    val updatedCollection = foundCollection.copy(tracks = foundCollection.tracks + existingTrack.uuid)
                    val updatedTrackMetadata = existingTrack.copy(collections = existingTrack.collections + foundCollection.uuid)
                    connection.autoCommit = false
                    val trackResult = updateTrack(updatedTrackMetadata)
                    val collectionResult = updateCollection(updatedCollection)
                    if (trackResult is Result.Success && collectionResult is Result.Success) {
                        connection.commit()
                        connection.autoCommit = true
                    } else {
                        connection.autoCommit = true
                        throw IllegalStateException("There was an error when adding track to collection. Track result was $trackResult, Collection result was $collectionResult")
                    }
                } else {
                    throw IllegalStateException("Collection ${collection.uuid} was not found in DB when trying to add a track to it.")
                }
            }.orElse { Unit }
            is Collection.NewCollection -> {
                connection.autoCommit = false
                val existingCollection = addCollection(collection, existingTrack)
                updateTrack(existingTrack.copy(collections = existingTrack.collections + existingCollection.uuid))
                connection.commit()
                connection.autoCommit = true
            }
        }

    private fun AudioTrackMetadata.toJsonString(): String =
        """{
            "artist": "$artist",
            "album": "$album",
            "title": "$title",
            "format": "$format",
            ${bitRate?.let { bitRate -> """"bitrate": "${bitRate.value}",""" }.orEmpty()}
            ${duration?.let { duration -> """"duration": "${duration.value}",""" }.orEmpty()}
            "size": "$fileSize",
            "recordedDate": "$recordedDate",
            "recordedTimestamp": "$recordedTimestamp",
            "recordedTimestampPrecision": "${recordedTimestampPrecision.name}",
            "uploadedTimestamp": "$uploadedTimestamp",
            "passwordProtectedLink": "$passwordProtectedLink",
            "path": "$path",
            ${collections.let { collections -> if (collections.isNotEmpty()) { """"collections": ${collections.map { "\"$it\"" }},""" } else { "" }}}
            "sha256": "$hash"
        }""".trimIndent()
}

private fun ResultSet.toAudioFileMetadata(): AudioTrackMetadata {
    val uuid = UUID.fromString(this.getString("id"))
    val postgresMetadata: PostgresAudioMetadata = this.getString("metadata").run {
        jacksonObjectMapper().readValue(this)
    }

    return AudioTrackMetadata(
        uuid,
        postgresMetadata.artist,
        postgresMetadata.album,
        postgresMetadata.title,
        postgresMetadata.format,
        postgresMetadata.bitrate.toBitRate(),
        postgresMetadata.duration?.toDuration(),
        postgresMetadata.size,
        postgresMetadata.recordedDate,
        postgresMetadata.recordedTimestamp.toZonedDateTime(),
        postgresMetadata.recordedTimestampPrecision.toChronoUnit(),
        postgresMetadata.uploadedTimestamp.toZonedDateTime(),
        postgresMetadata.passwordProtectedLink.toUri(),
        postgresMetadata.path,
        postgresMetadata.sha256,
        postgresMetadata.collections?.map { UUID.fromString(it) } ?: emptyList()
    )
}

private fun ResultSet.toCollection(): ExistingCollection {
    val uuid = UUID.fromString(this.getString("id"))
    val title = this.getString("name")
    @Suppress("UNCHECKED_CAST")
    val tracks: Set<UUID> = (this.getArray("tracks").array as Array<UUID>).toSet()

    return ExistingCollection(
        uuid,
        title,
        tracks
    )
}

private data class PostgresAudioMetadata(
    val artist: String,
    val album: String,
    val title: String,
    val format: String,
    val bitrate: String,
    val duration: String?,
    val size: Int,
    val recordedDate: String,
    val recordedTimestamp: String,
    val recordedTimestampPrecision: String,
    val uploadedTimestamp: String,
    val passwordProtectedLink: String,
    val path: String,
    val sha256: String,
    val collections: List<String>?
)
