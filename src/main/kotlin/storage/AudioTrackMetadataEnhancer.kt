package storage

import result.Error
import result.Result
import result.map

object AudioTrackMetadataEnhancer {
    fun AudioTrackMetadata.enhanceWithTakeNumber(metadataStorage: MetadataStorage): Result<Error, EnhancedAudioTrackMetadata> =
        metadataStorage.tracks()
            .map { tracks -> tracks.filter { it.recordedTimestamp.toLocalDate() == recordedTimestamp.toLocalDate() } }
            .map { tracks -> tracks.enhanceWithTakeNumber().find { it.base.uuid == uuid }!! }

    fun List<AudioTrackMetadata>.enhanceWithTakeNumber(): List<EnhancedAudioTrackMetadata> =
        groupBy { it.title.toLowerCase() }.map { entry ->
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
