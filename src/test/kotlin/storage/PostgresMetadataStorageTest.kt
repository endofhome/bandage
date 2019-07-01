package storage

import com.natpryce.hamkrest.assertion.assertThat
import com.natpryce.hamkrest.equalTo
import exampleAudioTrackMetadata
import org.junit.jupiter.api.Test

class PostgresMetadataStorageTest {
    @Test
    fun `round trip serialisation of audio track metadata`() = with(PostgresMetadataStorage.JsonSerialisation) {
        val originalMetadata = exampleAudioTrackMetadata
        val jsonString = originalMetadata.toJsonString()
        assertThat(jsonString.toAudioTrackMetadataWith(originalMetadata.uuid), equalTo(originalMetadata))
    }
}