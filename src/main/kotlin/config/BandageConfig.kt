package config

import Bandage.StaticConfig.appName
import config.BandageConfigItem.API_KEY
import config.BandageConfigItem.DROPBOX_ACCESS_TOKEN
import config.BandageConfigItem.DROPBOX_LINK_PASSWORD
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
    object DROPBOX_ACCESS_TOKEN : BandageConfigItem("${appName}_DROPBOX_ACCESS_TOKEN")
    object DROPBOX_LINK_PASSWORD : BandageConfigItem("${appName}_DROPBOX_LINK_PASSWORD")
    object SENTRY_DSN : BandageConfigItem("${appName}_SENTRY_DSN")
}

object BandageConfig : RequiredConfig() {
    override fun values() = setOf(
        API_KEY,
        PASSWORD,
        USER_ONE_FULL_NAME,
        USER_TWO_FULL_NAME,
        USER_THREE_FULL_NAME,
        DROPBOX_ACCESS_TOKEN,
        DROPBOX_LINK_PASSWORD,
        SENTRY_DSN
    )
}
