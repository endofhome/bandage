package storage

import result.Error
import result.Result
import result.map

object AudioTrackMetadataEnhancer {
    fun AudioTrackMetadata.enhanceWithTakeNumber(metadataStorage: MetadataStorage): Result<Error, EnhancedAudioTrackMetadata> =
        metadataStorage.tracks()
            .map { tracks -> tracks.filter { it.recordedTimestamp.toLocalDate() == recordedTimestamp.toLocalDate() } }
            .map { tracks -> tracks.enhanceWithTakeNumber().find { it.basicMetadata.uuid == uuid }!! }

    fun List<AudioTrackMetadata>.enhanceWithTakeNumber(): List<EnhancedAudioTrackMetadata> =
        groupBy { it.title }.map { entry ->
            if (entry.key != "untitled" && entry.value.size > 1) {
                entry.value.sortedBy { it.recordedTimestamp }.mapIndexed { index, audioTrackMetadata ->
                    EnhancedAudioTrackMetadata(audioTrackMetadata, index + 1)
                }
            } else {
                entry.value.map {
                    EnhancedAudioTrackMetadata(it)
                }
            }
        }.flatten()
         .sortedBy { it.basicMetadata.recordedTimestamp }

    data class EnhancedAudioTrackMetadata(val basicMetadata: AudioTrackMetadata, val takeNumber: Int? = null)
}
