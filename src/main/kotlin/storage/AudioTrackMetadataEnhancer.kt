package storage

import result.Error
import result.Result
import result.map
import result.mapNullToFailure

object AudioTrackMetadataEnhancer {
    fun AudioTrackMetadata.enhanceWithTakeNumber(metadataStorage: MetadataStorage): Result<Error, EnhancedAudioTrackMetadata> =
        metadataStorage.tracks()
            .map { tracks -> tracks.filter { it.recordedTimestamp.toLocalDate() == recordedTimestamp.toLocalDate() } }
            .map { tracks -> tracks.enhanceWithTakeNumber() }
            .map { tracks -> tracks.find { it.base.uuid == uuid } }
            .mapNullToFailure()

    fun List<AudioTrackMetadata>.enhanceWithTakeNumber(): List<EnhancedAudioTrackMetadata> =
        groupBy { it.preferredTitle().first.toLowerCase() }.map { entry ->
            if (listOf("untitled", "improv").contains(entry.key).not()  && entry.value.size > 1) {
                entry.value.sortedBy { it.recordedTimestamp }.mapIndexed { index, audioTrackMetadata ->
                    EnhancedAudioTrackMetadata(audioTrackMetadata, index + 1)
                }
            } else {
                entry.value.map {
                    EnhancedAudioTrackMetadata(it)
                }
            }
        }.flatten()
         .sortedBy { it.base.recordedTimestamp }

    data class EnhancedAudioTrackMetadata(val base: AudioTrackMetadata, val takeNumber: Int? = null)
}
