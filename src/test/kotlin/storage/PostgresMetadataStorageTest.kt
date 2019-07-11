package storage

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import exampleAudioTrackMetadata
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class PostgresMetadataStorageTest {
    @Test
    fun `round trip serialisation of audio track metadata`() = with(PostgresMetadataStorage.JsonSerialisation) {
        val originalMetadata = exampleAudioTrackMetadata.copy(workingTitles = listOf("a single working title"))
        val jsonString = originalMetadata.toJsonString()
        assertThat(jsonString.toAudioTrackMetadataWith(originalMetadata.uuid), equalTo(originalMetadata))
    }

    @Test
    fun `more than one working title is not currently supported when converting to JSON string`() = with(PostgresMetadataStorage.JsonSerialisation) {
        val originalMetadata = exampleAudioTrackMetadata

        val exception = assertThrows<IllegalStateException> {
            originalMetadata.toJsonString()
        }

        assertThat(exception.message!!, equalTo("Working titles are temporarily limited to one, ${originalMetadata.uuid} has 2"))
    }

    @Test
    fun `more than one working title is not currently supported when converting from JSON string`() = with(PostgresMetadataStorage.JsonSerialisation) {
        val jsonString = exampleAudioTrackMetadata.copy(workingTitles = listOf("a single working title")).toJsonString().replace("[\"a single working title\"]", "[\"first working title\", \"second working title\"]")

        val uuid = UUID.randomUUID()

        val exception = assertThrows<IllegalStateException> {
            jsonString.toAudioTrackMetadataWith(uuid)
        }

        assertThat(exception.message!!, equalTo("Working titles are temporarily limited to one, $uuid has 2"))
    }
}