package storage

import config.Configuration
import java.util.UUID

class StubMetadataStorage(private val metadata: MutableList<AudioFileMetadata>) : DummyMetadataStorage() {
    override fun all(): List<AudioFileMetadata> = metadata

    override fun find(uuid: UUID): AudioFileMetadata? =
        metadata.find { audioFileMetadata -> audioFileMetadata.uuid == uuid }

    override fun write(newMetadata: List<AudioFileMetadata>) {
        metadata += newMetadata
    }
}

open class DummyMetadataStorage : MetadataStorage {
    override fun all() = emptyList<AudioFileMetadata>()
    override fun find(uuid: UUID): AudioFileMetadata? = null
    override fun write(newMetadata: List<AudioFileMetadata>): Unit = TODO("not implemented")
    override fun update(updatedMetadata: AudioFileMetadata) = TODO("not implemented")
}


class StubMetadataStorageFactory(private val metadata: MutableList<AudioFileMetadata>): DummyMetadataStorageFactory() {
    override fun invoke(config: Configuration): MetadataStorage = StubMetadataStorage(metadata)
}

open class DummyMetadataStorageFactory: MetadataStorageFactory {
    override operator fun invoke(config: Configuration): MetadataStorage = DummyMetadataStorage()
}
