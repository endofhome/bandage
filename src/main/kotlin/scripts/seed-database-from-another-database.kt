package scripts

import Bandage
import config.BandageConfig
import config.ValidateConfig
import result.map
import storage.LocalCsvMetadataStorage
import storage.PostgresMetadataStorage

fun main() {
    val config = ValidateConfig(BandageConfig, Bandage.StaticConfig.configurationFilesDir)

    val sourceMetadataStorage = LocalCsvMetadataStorage
    val destinationMetadataStorage = PostgresMetadataStorage(config)

    sourceMetadataStorage.tracks().map { all ->
        destinationMetadataStorage.addTracks(all)
    }
}
