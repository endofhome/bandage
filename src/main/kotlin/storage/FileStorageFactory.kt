package storage

import Bandage.StaticConfig.appName
import config.BandageConfigItem.DROPBOX_ACCESS_TOKEN
import config.Configuration

interface FileStorageFactory {
    operator fun invoke(config: Configuration): FileStorage
}

object DropboxFileStorageFactory: FileStorageFactory {
    override operator fun invoke(config: Configuration): FileStorage =
        DropboxFileStorage(HttpDropboxClient(appName, config.get(DROPBOX_ACCESS_TOKEN)))
}

class StubFileStorageFactory(private val files: Map<FileUrl, FileStringData>): FileStorageFactory {
    override fun invoke(config: Configuration): FileStorage = StubFileStorage(files)
}

object DummyFileStorageFactory: FileStorageFactory {
    override operator fun invoke(config: Configuration): FileStorage = DummyFileStorage
}
