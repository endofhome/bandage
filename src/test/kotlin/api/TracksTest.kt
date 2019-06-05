package api

import exampleAudioTrackMetadata
import org.http4k.testing.Approver
import org.http4k.testing.JsonApprovalTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import storage.StubMetadataStorage
import java.util.UUID

@ExtendWith(JsonApprovalTest::class)
internal class TracksTest {
    @Test
    fun `tracks are available via API`(approver: Approver) {
        val uuids = (1..5).map { UUID.nameUUIDFromBytes(it.toString().toByteArray()) }
        val metadataStorage = StubMetadataStorage(mutableListOf(
            exampleAudioTrackMetadata.copy(uuid = uuids[0]),
            exampleAudioTrackMetadata.copy(uuid = uuids[1], title = "in a collection", collections = listOf(uuids[2])),
            exampleAudioTrackMetadata.copy(uuid = uuids[3],title = "no bitrate", bitRate = null),
            exampleAudioTrackMetadata.copy(uuid = uuids[4],title = "no duration", duration = null)
        ))

        val response = Tracks(metadataStorage)

        approver.assertApproved(response)
    }
}
