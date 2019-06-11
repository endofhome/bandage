package storage

import Bandage
import config.BandageConfig
import config.BandageConfigItem
import config.ValidateConfig
import config.withDynamicDatabaseUrlFrom
import org.flywaydb.core.Flyway
import org.postgresql.ds.PGSimpleDataSource

object PostgresMetadataMigrations {
    @JvmStatic
    fun main(args: Array<String>) {
        val config = ValidateConfig(
            BandageConfig,
            Bandage.StaticConfig.configurationFilesDir).withDynamicDatabaseUrlFrom(System.getenv("DATABASE_URL")
        )

        val datasource = PGSimpleDataSource().apply {
            serverName = config.get(BandageConfigItem.METADATA_DB_HOST)
            portNumber = config.get(BandageConfigItem.METADATA_DB_PORT).toInt()
            databaseName = config.get(BandageConfigItem.METADATA_DB_NAME)
            user = config.get(BandageConfigItem.METADATA_DB_USER)
            password = config.get(BandageConfigItem.METADATA_DB_PASSWORD)
            sslMode = config.get(BandageConfigItem.METADATA_DB_SSL_MODE)
        }

        val flyway = Flyway.configure().dataSource(datasource).baselineOnMigrate(true).load()

        flyway.migrate()
    }
}
