package config

import config.BandageConfigItem.API_KEY
import config.BandageConfigItem.PASSWORD
import config.BandageConfigItem.SENTRY_DSN
import config.BandageConfigItem.USER_ONE_FULL_NAME
import config.BandageConfigItem.USER_THREE_FULL_NAME
import config.BandageConfigItem.USER_TWO_FULL_NAME

const val prefix = "BANDAGE"

sealed class BandageConfigItem(override val name: String) : RequiredConfigItem {
    object API_KEY : BandageConfigItem("${prefix}_API_KEY")
    object PASSWORD : BandageConfigItem("${prefix}_PASSWORD")
    object USER_ONE_FULL_NAME : BandageConfigItem("${prefix}_USER_ONE_FULL_NAME")
    object USER_TWO_FULL_NAME : BandageConfigItem("${prefix}_USER_TWO_FULL_NAME")
    object USER_THREE_FULL_NAME : BandageConfigItem("${prefix}_USER_THREE_FULL_NAME")
    object SENTRY_DSN : BandageConfigItem("${prefix}_SENTRY_DSN")
}

object BandageConfig : RequiredConfig() {
    override fun values() = setOf(
        API_KEY,
        PASSWORD,
        USER_ONE_FULL_NAME,
        USER_TWO_FULL_NAME,
        USER_THREE_FULL_NAME,
        SENTRY_DSN
    )
}
