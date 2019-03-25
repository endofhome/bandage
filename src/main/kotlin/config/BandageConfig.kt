package config

import config.BandageConfigItem.API_KEY
import config.BandageConfigItem.PASSWORD
import config.BandageConfigItem.USER_ONE_FULL_NAME
import config.BandageConfigItem.USER_THREE_FULL_NAME
import config.BandageConfigItem.USER_TWO_FULL_NAME

const val prefix = "BANDAGE"

sealed class BandageConfigItem(override val name: String) : RequiredConfigItem {
    class API_KEY : BandageConfigItem("${prefix}_API_KEY")
    class PASSWORD : BandageConfigItem("${prefix}_PASSWORD")
    class USER_ONE_FULL_NAME : BandageConfigItem("${prefix}_USER_ONE_FULL_NAME")
    class USER_TWO_FULL_NAME : BandageConfigItem("${prefix}_USER_TWO_FULL_NAME")
    class USER_THREE_FULL_NAME : BandageConfigItem("${prefix}_USER_THREE_FULL_NAME")
}

class BandageConfig : RequiredConfig() {
    override fun values() = setOf(
        API_KEY(),
        PASSWORD(),
        USER_ONE_FULL_NAME(),
        USER_TWO_FULL_NAME(),
        USER_THREE_FULL_NAME()
    )
}
