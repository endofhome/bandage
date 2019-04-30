package storage

import Bandage.StaticConfig.appName
import config.BandageConfigItem.DROPBOX_ACCESS_TOKEN
import config.Configuration

interface MetadataStorageFactory {
    operator fun invoke(config: Configuration): MetadataStorage
}

object DropboxCsvMetadataStorageFactory: MetadataStorageFactory {
    override operator fun invoke(config: Configuration): MetadataStorage =
        DropboxCsvMetadataStorage(HttpDropboxClient(appName, config.get(DROPBOX_ACCESS_TOKEN)))
}

object LocalCsvMetadataStorageFactory: MetadataStorageFactory {
    override operator fun invoke(config: Configuration): MetadataStorage = LocalCsvMetadataStorage
}

class StubMetadataStorageFactory(private val metadata: MutableList<AudioFileMetadata>): MetadataStorageFactory {
    override fun invoke(config: Configuration): MetadataStorage = StubMetadataStorage(metadata)
}

object DummyMetadataStorageFactory: MetadataStorageFactory {
    override operator fun invoke(config: Configuration): MetadataStorage = DummyMetadataStorage
}
