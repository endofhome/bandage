package config

import Bandage.StaticConfig.appName
import config.BandageConfigItem.API_KEY
import config.BandageConfigItem.DISABLE_ID3_TAGGING_ON_THE_FLY
import config.BandageConfigItem.DROPBOX_ACCESS_TOKEN
import config.BandageConfigItem.DROPBOX_LINK_PASSWORD
import config.BandageConfigItem.ENABLE_NEW_PLAYER
import config.BandageConfigItem.METADATA_DB_HOST
import config.BandageConfigItem.METADATA_DB_NAME
import config.BandageConfigItem.METADATA_DB_PASSWORD
import config.BandageConfigItem.METADATA_DB_PORT
import config.BandageConfigItem.METADATA_DB_SSL_MODE
import config.BandageConfigItem.METADATA_DB_USER
import config.BandageConfigItem.PASSWORD
import config.BandageConfigItem.SENTRY_DSN
import config.BandageConfigItem.USER_ONE_FULL_NAME
import config.BandageConfigItem.USER_THREE_FULL_NAME
import config.BandageConfigItem.USER_TWO_FULL_NAME

sealed class BandageConfigItem(override val name: String) : RequiredConfigItem {
    object API_KEY : BandageConfigItem("${appName}_API_KEY")
    object PASSWORD : BandageConfigItem("${appName}_PASSWORD")
    object USER_ONE_FULL_NAME : BandageConfigItem("${appName}_USER_ONE_FULL_NAME")
    object USER_TWO_FULL_NAME : BandageConfigItem("${appName}_USER_TWO_FULL_NAME")
    object USER_THREE_FULL_NAME : BandageConfigItem("${appName}_USER_THREE_FULL_NAME")
    object METADATA_DB_HOST : BandageConfigItem("${appName}_METADATA_DB_HOST")
    object METADATA_DB_PORT : BandageConfigItem("${appName}_METADATA_DB_PORT")
    object METADATA_DB_NAME : BandageConfigItem("${appName}_METADATA_DB_NAME")
    object METADATA_DB_USER : BandageConfigItem("${appName}_METADATA_DB_USER")
    object METADATA_DB_PASSWORD : BandageConfigItem("${appName}_METADATA_DB_PASSWORD")
    object METADATA_DB_SSL_MODE : BandageConfigItem("${appName}_METADATA_DB_SSL_MODE")
    object DROPBOX_ACCESS_TOKEN : BandageConfigItem("${appName}_DROPBOX_ACCESS_TOKEN")
    object DROPBOX_LINK_PASSWORD : BandageConfigItem("${appName}_DROPBOX_LINK_PASSWORD")
    object DISABLE_ID3_TAGGING_ON_THE_FLY : BandageConfigItem("${appName}_DISABLE_ID3_TAGGING_ON_THE_FLY")
    object ENABLE_NEW_PLAYER : BandageConfigItem("${appName}_ENABLE_NEW_PLAYER")
    object SENTRY_DSN : BandageConfigItem("${appName}_SENTRY_DSN")
}

object BandageConfig : RequiredConfig() {
    override fun values() = setOf(
        API_KEY,
        PASSWORD,
        USER_ONE_FULL_NAME,
        USER_TWO_FULL_NAME,
        USER_THREE_FULL_NAME,
        METADATA_DB_HOST,
        METADATA_DB_PORT,
        METADATA_DB_NAME,
        METADATA_DB_USER,
        METADATA_DB_PASSWORD,
        METADATA_DB_SSL_MODE,
        DROPBOX_ACCESS_TOKEN,
        DROPBOX_LINK_PASSWORD,
        DISABLE_ID3_TAGGING_ON_THE_FLY,
        ENABLE_NEW_PLAYER,
        SENTRY_DSN
    )
}

fun Configuration.withDynamicDatabaseUrlFrom(urlString: String?): Configuration =
    urlString?.let { databaseUrl ->
        val protocol = databaseUrl.substringBefore("://")
        val user = databaseUrl.substringAfter("$protocol://").substringBefore(":")
        val password = databaseUrl.substringAfter("$user:").substringBefore("@")
        val host = databaseUrl.substringAfter("$password@").substringBefore(":")
        val port = databaseUrl.substringAfter("$host:").substringBefore("/")
        val database = databaseUrl.substringAfter("$port/")

        this.withOverride(METADATA_DB_HOST, host)
            .withOverride(METADATA_DB_PORT, port)
            .withOverride(METADATA_DB_NAME, database)
            .withOverride(METADATA_DB_USER, user)
            .withOverride(METADATA_DB_PASSWORD, password)
    } ?: this
