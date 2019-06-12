package storage

import config.Configuration
import result.Result
import result.Result.Success
import result.asSuccess
import java.util.UUID

class StubMetadataStorage(private val metadata: MutableList<AudioTrackMetadata>) : DummyMetadataStorage() {
    override fun all(): Result<Error, List<AudioTrackMetadata>> = metadata.asSuccess()

    override fun find(uuid: UUID): Result<Error, AudioTrackMetadata?> =
        metadata.find { audioFileMetadata -> audioFileMetadata.uuid == uuid }.asSuccess()

    override fun write(newMetadata: List<AudioTrackMetadata>) {
        metadata += newMetadata
    }
}

open class DummyMetadataStorage : MetadataStorage {
    override fun all(): Result<Error, List<AudioTrackMetadata>> = Success(emptyList())
    override fun find(uuid: UUID): Result<Error, AudioTrackMetadata?> = Success(null)
    override fun write(newMetadata: List<AudioTrackMetadata>): Unit = TODO("not implemented")
    override fun update(updatedMetadata: AudioTrackMetadata) = TODO("not implemented")
    override fun findCollection(uuid: UUID) = TODO("not implemented")
}

class StubMetadataStorageFactory(private val metadata: MutableList<AudioTrackMetadata>): DummyMetadataStorageFactory() {
    override fun invoke(config: Configuration): MetadataStorage = StubMetadataStorage(metadata)
}

open class DummyMetadataStorageFactory: MetadataStorageFactory {
    override operator fun invoke(config: Configuration): MetadataStorage = DummyMetadataStorage()
}
