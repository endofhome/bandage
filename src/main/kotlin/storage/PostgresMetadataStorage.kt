package storage

import Logging.logger
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import config.BandageConfigItem.METADATA_DB_HOST
import config.BandageConfigItem.METADATA_DB_NAME
import config.BandageConfigItem.METADATA_DB_PASSWORD
import config.BandageConfigItem.METADATA_DB_PORT
import config.BandageConfigItem.METADATA_DB_SSL_MODE
import config.BandageConfigItem.METADATA_DB_USER
import config.Configuration
import result.Error
import result.Result
import result.Result.Failure
import result.Result.Success
import result.asSuccess
import result.flatMap
import result.map
import result.orElse
import storage.Collection.ExistingCollection
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.format.DateTimeFormatter
import java.util.UUID

class PostgresMetadataStorage(config: Configuration, sslRequireModeOverride: Boolean? = null) : MetadataStorage {
    private val datasource = HikariDataSource(HikariConfig().apply {
        dataSourceClassName = "org.postgresql.ds.PGSimpleDataSource"
        dataSourceProperties.setProperty("serverName", config.get(METADATA_DB_HOST))
        dataSourceProperties.setProperty("portNumber", config.get(METADATA_DB_PORT))
        dataSourceProperties.setProperty("databaseName", config.get(METADATA_DB_NAME))
        dataSourceProperties.setProperty("sslMode", sslRequireModeOverride?.let { if (it) "require" else ""  } ?: config.get(METADATA_DB_SSL_MODE))
        username = config.get(METADATA_DB_USER)
        password = config.get(METADATA_DB_PASSWORD)
    })

    override fun tracks(): Result<Error, List<AudioTrackMetadata>> =
        try {
            // TODO - try to do this nicely in one query with a JOIN - multiple queries is really not nice

            val initialMetadata = datasource.connection.use { connection ->
                val statement = connection.prepareStatement("SELECT * FROM public.tracks")
                statement.executeQuery().use { resultSet ->
                    generateSequence {
                        if (resultSet.next()) resultSet.toAudioFileMetadata() else null
                    }.toList()
                }
            }
            initialMetadata.map {
                if (it.collections.isEmpty()) {
                    it
                } else {
                    val onlyFirstCollectionSupported = it.collections.first()
                    datasource.connection.use { connection ->
                        val statement = connection.prepareStatement("SELECT name FROM collections WHERE id = '${onlyFirstCollectionSupported.uuid}'")
                        statement.executeQuery().use { resultSet ->
                            resultSet.next()
                            it.copy(collections = listOf(it.collections.first().copy(title = resultSet.getString("name"))))
                        }
                    }
                }
            }.asSuccess()
        } catch (e: Exception) {
            val errorMessage = "Error reading all tracks metadata"
            logger.warn("$errorMessage\n${e.message}\n${e.stackTrace}")
            Failure(Error(errorMessage))
        }

    override fun findTrack(uuid: UUID): Result<Error, AudioTrackMetadata?> =
        // TODO - try to do this nicely in one query with a JOIN - multiple queries is really not nice

        try {
            val searchResult = datasource.connection.use { connection ->
                val statement = connection.prepareStatement("SELECT * FROM public.tracks WHERE id = '$uuid'")
                statement.executeQuery().use { resultSet ->
                    if (resultSet.next()) resultSet.toAudioFileMetadata() else null
                }
            }

            searchResult?.let {
                if (it.collections.isEmpty()) {
                    it
                } else {
                    val onlyFirstCollectionSupported = it.collections.first()
                    datasource.connection.use { connection ->
                        val statement = connection.prepareStatement("SELECT name FROM collections WHERE id = '${onlyFirstCollectionSupported.uuid}'")
                        statement.executeQuery().use { resultSet ->
                            resultSet.next()
                            it.copy(collections = listOf(it.collections.first().copy(title = resultSet.getString("name"))))
                        }
                    }
                }
            }.asSuccess()
        } catch (e: Exception) {
            Failure(Error("Error finding track $uuid in metadata storage"))

        }

    override fun addTracks(newMetadata: List<AudioTrackMetadata>) {
        datasource.connection.use { connection ->
            val preparedStatement = connection.prepareStatement("""
                INSERT INTO tracks VALUES ${newMetadata.joinToString(",") { "(?::uuid, ?::jsonb)" }};
            """.trimIndent())

            val uuidIndexes = 1..newMetadata.size * 2 step 2

            newMetadata.zip(uuidIndexes).forEach { (audioFileMetadata, uuidIndex) ->
                preparedStatement.setString(uuidIndex, audioFileMetadata.uuid.toString())
                preparedStatement.setString(uuidIndex + 1, with(JsonSerialisation) { audioFileMetadata.toJsonString() })
            }

            preparedStatement.use { statement ->
                statement.executeUpdate()
            }
        }

    }

    override fun updateTrack(updatedMetadata: AudioTrackMetadata): Result<Error, AudioTrackMetadata> =
        datasource.connection.use { connection ->
            updateTrack(updatedMetadata, connection)
        }

    fun updateTrack(updatedMetadata: AudioTrackMetadata, connection: Connection): Result<Error, AudioTrackMetadata> =
        findTrack(updatedMetadata.uuid).flatMap { track ->
            if (track != null) {
                val preparedStatement: PreparedStatement = connection.prepareStatement("""
                    UPDATE tracks SET metadata = ?::jsonb WHERE id = '${updatedMetadata.uuid}';
                """.trimIndent())

                preparedStatement.setString(1, with(JsonSerialisation) { updatedMetadata.toJsonString() })

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
            datasource.connection.use { connection ->
                val statement = connection.prepareStatement("SELECT * FROM public.collections WHERE id = '$uuid'")
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
        datasource.connection.use { connection ->
            val statement = connection.prepareStatement("""
            INSERT INTO collections VALUES (?::uuid, ?, ?::uuid[]);
        """.trimIndent()
        )

            statement.setString(1, upgradedCollection.uuid.toString())
            statement.setString(2, upgradedCollection.title)
            statement.setArray(3, connection.createArrayOf("UUID", upgradedCollection.tracks.toTypedArray()))

            statement.executeUpdate()
        }

        return upgradedCollection
    }

    override fun updateCollection(updatedCollection: ExistingCollection): Result<Error, ExistingCollection> =
        datasource.connection.use { connection ->
            updateCollection(updatedCollection, connection)
        }

    private fun updateCollection(updatedCollection: ExistingCollection, connection: Connection): Result<Error, ExistingCollection> =
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

    override fun addExistingTrackToCollection(existingTrack: AudioTrackMetadata, collection: Collection) {
        datasource.connection.also { it.autoCommit = false }.use { connection ->
            return when (collection) {
                is ExistingCollection -> findCollection(collection.uuid).map { foundCollection ->
                    if (foundCollection != null) {
                        val updatedCollection = foundCollection.copy(tracks = foundCollection.tracks + existingTrack.uuid)
                        val updatedTrackMetadata = existingTrack.copy(collections = existingTrack.collections + foundCollection)
                        val trackResult = updateTrack(updatedTrackMetadata, connection)
                        val collectionResult = updateCollection(updatedCollection, connection)
                        if (trackResult is Success && collectionResult is Success) {
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
                    updateTrack(
                        existingTrack.copy(collections = existingTrack.collections + existingCollection),
                        datasource.connection
                    )
                    connection.commit()
                    connection.autoCommit = true
                }
            }
        }
    }

    object JsonSerialisation {
        private val timestampFormatter = DateTimeFormatter.ISO_DATE_TIME

        private fun tooManyWorkingTitles(trackUuid: UUID, numberOfWorkingTitles: Int) =
            IllegalStateException("Working titles are temporarily limited to one, $trackUuid has $numberOfWorkingTitles")

        fun AudioTrackMetadata.toJsonString(): String {
            if (workingTitles.size > 1) throw tooManyWorkingTitles(uuid, workingTitles.size)

            return """{
                    "artist": "$artist",
                    "album": "$album",
                    "title": "$title",
                    "workingTitles": ${workingTitles.map { workingTitle -> "\"$workingTitle\"" }},
                    "format": "$format",
                    ${bitRate?.let { bitRate -> """"bitrate": "${bitRate.value}",""" }.orEmpty()}
                    ${duration?.let { duration -> """"duration": "${duration.value}",""" }.orEmpty()}
                    "size": "$fileSize",
                    "recordedDate": "$recordedDate",
                    "recordedTimestamp": "${timestampFormatter.format(recordedTimestamp)}",
                    "recordedTimestampPrecision": "${recordedTimestampPrecision.name}",
                    "uploadedTimestamp": "$uploadedTimestamp",
                    "passwordProtectedLink": "$passwordProtectedLink",
                    "path": "$path",
                    ${collections.let { collections ->
                if (collections.isNotEmpty()) { """"collections": ${collections.map { "\"${it.uuid}\"" }},""" } else { "" }
            }}
                    "sha256": "$hash"
                }""".trimIndent()
        }

        fun String.toAudioTrackMetadataWith(uuid: UUID): AudioTrackMetadata {
            val postgresMetadata: PostgresAudioMetadata = run {
                jacksonObjectMapper().readValue(this)
            }

            postgresMetadata.workingTitles?.let { if (it.size > 1) throw tooManyWorkingTitles(uuid, it.size) }

            return AudioTrackMetadata(
                uuid,
                postgresMetadata.artist,
                postgresMetadata.album,
                postgresMetadata.title,
                postgresMetadata.workingTitles?.map { it }?.take(1) ?: emptyList(),
                postgresMetadata.format,
                postgresMetadata.bitrate?.toBitRate(),
                postgresMetadata.duration?.toDuration(),
                postgresMetadata.size,
                postgresMetadata.recordedDate,
                postgresMetadata.recordedTimestamp.toZonedDateTime(),
                postgresMetadata.recordedTimestampPrecision.toChronoUnit(),
                postgresMetadata.uploadedTimestamp.toZonedDateTime(),
                postgresMetadata.passwordProtectedLink.toUri(),
                postgresMetadata.path,
                postgresMetadata.sha256,
                // TODO again, not pleasant as the title and tracks are set to empty.
                postgresMetadata.collections?.map { ExistingCollection(UUID.fromString(it), "", emptySet()) } ?: emptyList()
            )
        }
    }
}

private fun ResultSet.toAudioFileMetadata(): AudioTrackMetadata {
    val uuid = UUID.fromString(this.getString("id"))
    val metadataJsonString = this.getString("metadata")
    return with(PostgresMetadataStorage.JsonSerialisation) {
        metadataJsonString.toAudioTrackMetadataWith(uuid)
    }
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
    val workingTitles: List<String>?,
    val format: String,
    val bitrate: String?,
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
