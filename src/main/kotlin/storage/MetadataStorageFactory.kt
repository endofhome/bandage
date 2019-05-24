package storage

import Bandage.StaticConfig.appName
import config.BandageConfigItem.DROPBOX_ACCESS_TOKEN
import config.Configuration

interface MetadataStorageFactory {
    operator fun invoke(config: Configuration): MetadataStorage
}

object PostgresMetadataStorageFactory: MetadataStorageFactory {
    override operator fun invoke(config: Configuration): MetadataStorage = PostgresMetadataStorage(config)
}

object DropboxCsvMetadataStorageFactory: MetadataStorageFactory {
    override operator fun invoke(config: Configuration): MetadataStorage =
        DropboxCsvMetadataStorage(HttpDropboxClient(appName, config.get(DROPBOX_ACCESS_TOKEN)))
}

object LocalCsvMetadataStorageFactory: MetadataStorageFactory {
    override operator fun invoke(config: Configuration): MetadataStorage = LocalCsvMetadataStorage
}
