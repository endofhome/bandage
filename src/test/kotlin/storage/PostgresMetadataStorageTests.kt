package storage

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import exampleAudioTrackMetadata
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import storage.PostgresMetadataStorage.PostgresAudioMetadata
import storage.PostgresMetadataStorage.PostgresTrack
import storage.PostgresMetadataStorage.PostgresWaveform
import java.util.UUID

class PostgresMetadataStorageTests {
    @Test
    fun `round trip serialisation of audio track metadata`() = with(PostgresMetadataStorage.JsonSerialisation) {
        val originalMetadata = exampleAudioTrackMetadata.copy(workingTitles = listOf("a single working title"))

        val metadatajsonString = originalMetadata.toMetadataJsonString()
        val waveformJsonString = originalMetadata.toWaveformJsonString()
        val postgresMetadata = jacksonObjectMapper().readValue<PostgresAudioMetadata>(metadatajsonString)
        val postgresWaveform =
            if (waveformJsonString != null) run { jacksonObjectMapper().readValue<PostgresWaveform?>(waveformJsonString) } else null
        val postgresTrack = PostgresTrack(originalMetadata.uuid, postgresMetadata, postgresWaveform)

        assertThat(postgresTrack.toAudioTrackMetadata(), equalTo(originalMetadata))
    }

    @Test
    fun `more than one working title is not currently supported when converting to JSON string`() = with(PostgresMetadataStorage.JsonSerialisation) {
        val originalMetadata = exampleAudioTrackMetadata

        val exception = assertThrows<IllegalStateException> {
            originalMetadata.toMetadataJsonString()
        }

        assertThat(exception.message!!, equalTo("Working titles are temporarily limited to one, ${originalMetadata.uuid} has 2"))
    }

    @Test
    fun `more than one working title is not currently supported when converting from JSON string`() = with(PostgresMetadataStorage.JsonSerialisation) {
        val jsonString = exampleAudioTrackMetadata.copy(workingTitles = listOf("a single working title")).toMetadataJsonString().replace("[\"a single working title\"]", "[\"first working title\", \"second working title\"]")

        val uuid = UUID.randomUUID()
        val postgresMetadata = jacksonObjectMapper().readValue<PostgresAudioMetadata>(jsonString)
        val postgresTrack = PostgresTrack(uuid, postgresMetadata, null)

        val exception = assertThrows<IllegalStateException> {
            postgresTrack.toAudioTrackMetadata()
        }

        assertThat(exception.message!!, equalTo("Working titles are temporarily limited to one, $uuid has 2"))
    }
}