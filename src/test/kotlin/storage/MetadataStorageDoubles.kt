package storage

import config.Configuration
import result.Error
import result.Result
import result.Result.Success
import result.asSuccess
import storage.Collection.NewCollection
import java.util.UUID

open class StubMetadataStorage(private val metadata: MutableList<AudioTrackMetadata>) : DummyMetadataStorage() {
    override fun tracks(): Result<Error, List<AudioTrackMetadata>> = metadata.asSuccess()

    override fun findTrack(uuid: UUID): Result<Error, AudioTrackMetadata?> =
        metadata.find { audioFileMetadata -> audioFileMetadata.uuid == uuid }.asSuccess()

    override fun addTracks(newMetadata: List<AudioTrackMetadata>) {
        metadata += newMetadata
    }

    override fun updateTrack(updatedMetadata: AudioTrackMetadata): Result<Error, AudioTrackMetadata> {
        metadata.removeIf { it.uuid == updatedMetadata.uuid }
        metadata.add(updatedMetadata)
        return updatedMetadata.asSuccess()
    }
}

open class DummyMetadataStorage : MetadataStorage {
    override fun tracks(): Result<Error, List<AudioTrackMetadata>> = Success(emptyList())
    override fun findTrack(uuid: UUID): Result<Error, AudioTrackMetadata?> = Success(null)
    override fun addTracks(newMetadata: List<AudioTrackMetadata>): Unit = error("not implemented")
    override fun updateTrack(updatedMetadata: AudioTrackMetadata): Result<Error, AudioTrackMetadata> = error("not implemented")
    override fun addExistingTrackToCollection(existingTrack: AudioTrackMetadata, collection: Collection) =
        error("not yet implemented")
    override fun findCollection(uuid: UUID) = error("not implemented")
    override fun addCollection(newCollection: NewCollection, firstElement: AudioTrackMetadata
    ) = error("not yet implemented")
    override fun updateCollection(updatedCollection: Collection.ExistingCollection) = error("not yet implemented")
}

class StubMetadataStorageFactory(private val metadata: MutableList<AudioTrackMetadata>): DummyMetadataStorageFactory() {
    override fun invoke(config: Configuration): MetadataStorage = StubMetadataStorage(metadata)
}

open class DummyMetadataStorageFactory: MetadataStorageFactory {
    override operator fun invoke(config: Configuration): MetadataStorage = DummyMetadataStorage()
}
