package api

import exampleAudioTrackMetadata
import org.http4k.testing.Approver
import org.http4k.testing.JsonApprovalTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import storage.Collection
import storage.StubMetadataStorage
import java.time.Instant.EPOCH
import java.time.ZoneOffset.UTC
import java.time.temporal.ChronoUnit
import java.util.UUID

@ExtendWith(JsonApprovalTest::class)
internal class TracksTests {
    @Test
    fun `tracks are available via API`(approver: Approver) {
        val uuids = (1..5).map { UUID.nameUUIDFromBytes(it.toString().toByteArray()) }
        val metadataStorage = StubMetadataStorage(mutableListOf(
            exampleAudioTrackMetadata.copy(uuid = uuids[0], recordedTimestamp = 1L.daysAfterEpoch()),
            exampleAudioTrackMetadata.copy(uuid = uuids[1], title = "in a collection", collections = listOf(Collection.ExistingCollection(uuids[2], "Awesome collection", emptySet())), recordedTimestamp = 2L.daysAfterEpoch()),
            exampleAudioTrackMetadata.copy(uuid = uuids[3],title = "no bitrate", bitRate = null, recordedTimestamp = 3L.daysAfterEpoch()),
            exampleAudioTrackMetadata.copy(uuid = uuids[4],title = "no duration", duration = null, recordedTimestamp = 4L.daysAfterEpoch())
            )
        )

        val response = Tracks(metadataStorage)

        approver.assertApproved(response)
    }

    private fun Long.daysAfterEpoch() = EPOCH.plus(this, ChronoUnit.DAYS).atZone(UTC)
}
