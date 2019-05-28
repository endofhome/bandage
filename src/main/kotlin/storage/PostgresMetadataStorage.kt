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

    override fun all(): List<AudioFileMetadata> =
        datasource.connection.prepareStatement("SELECT * FROM public.tracks")
            .use { statement ->
                statement.executeQuery()
                    .use { resultSet ->
                        generateSequence {
                            if (resultSet.next()) resultSet.toAudioFileMetadata() else null
                        }.toList()
                    }
            }

    override fun find(uuid: UUID): AudioFileMetadata? =
        datasource.connection.prepareStatement("SELECT * FROM public.tracks WHERE id = '$uuid'")
            .use { statement ->
                statement.executeQuery()
                    .use { resultSet ->
                        if (resultSet.next()) resultSet.toAudioFileMetadata() else null
                    }
            }

    override fun write(newMetadata: List<AudioFileMetadata>) {
        val preparedStatement = datasource.connection.prepareStatement("""
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

    override fun update(updatedMetadata: AudioFileMetadata) {
        find(updatedMetadata.uuid)?.let {
            val preparedStatement = datasource.connection.prepareStatement("""
                UPDATE tracks SET metadata = ?::jsonb WHERE id = '${updatedMetadata.uuid}';
            """.trimIndent())

            preparedStatement.setString(1, updatedMetadata.toJsonString())

            preparedStatement.use { statement ->
                statement.executeUpdate()
            }
        } ?: throw RuntimeException("UUID ${updatedMetadata.uuid} not found.")
    }

    private fun AudioFileMetadata.toJsonString(): String =
        """{
            "artist": "$artist",
            "album": "$album",
            "title": "$title",
            "format": "$format",
            ${bitRate?.let { bitRate -> """"bitrate": "${bitRate.value}",""" }}
            ${duration?.let { duration -> """"duration": "${duration.value}",""" }}
            "size": "$size",
            "recordedDate": "$recordedDate",
            "passwordProtectedLink": "$passwordProtectedLink",
            "path": "$path",
            "sha256": "$hash"
        }""".trimIndent()
}

private fun ResultSet.toAudioFileMetadata(): AudioFileMetadata {
    val uuid = UUID.fromString(this.getString("id"))
    val postgresMetadata: PostgresAudioMetadata = this.getString("metadata").run {
        jacksonObjectMapper().readValue(this)
    }

    return AudioFileMetadata(
        uuid,
        postgresMetadata.artist,
        postgresMetadata.album,
        postgresMetadata.title,
        postgresMetadata.format,
        postgresMetadata.bitrate.toBitRate(),
        postgresMetadata.duration?.toDuration(),
        postgresMetadata.size,
        postgresMetadata.recordedDate,
        postgresMetadata.passwordProtectedLink,
        postgresMetadata.path,
        postgresMetadata.sha256
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
    val passwordProtectedLink: String,
    val path: String,
    val sha256: String
)
