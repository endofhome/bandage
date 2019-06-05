package storage

import config.Configuration
import java.util.UUID

class StubMetadataStorage(private val metadata: MutableList<AudioTrackMetadata>) : DummyMetadataStorage() {
    override fun all(): List<AudioTrackMetadata> = metadata

    override fun find(uuid: UUID): AudioTrackMetadata? =
        metadata.find { audioFileMetadata -> audioFileMetadata.uuid == uuid }

    override fun write(newMetadata: List<AudioTrackMetadata>) {
        metadata += newMetadata
    }
}

open class DummyMetadataStorage : MetadataStorage {
    override fun all() = emptyList<AudioTrackMetadata>()
    override fun find(uuid: UUID): AudioTrackMetadata? = null
    override fun write(newMetadata: List<AudioTrackMetadata>): Unit = TODO("not implemented")
    override fun update(updatedMetadata: AudioTrackMetadata) = TODO("not implemented")
}


class StubMetadataStorageFactory(private val metadata: MutableList<AudioTrackMetadata>): DummyMetadataStorageFactory() {
    override fun invoke(config: Configuration): MetadataStorage = StubMetadataStorage(metadata)
}

open class DummyMetadataStorageFactory: MetadataStorageFactory {
    override operator fun invoke(config: Configuration): MetadataStorage = DummyMetadataStorage()
}
