package config

fun dummyConfiguration(): Configuration {
    val baseConfigValues = BandageConfig.values().associate { it to "unset" }.toMutableMap()
    val configValues: Map<BandageConfigItem, String> = baseConfigValues.apply {
        removeAndSet(config.BandageConfigItem.API_KEY, "some-key")
        removeAndSet(config.BandageConfigItem.PASSWORD, "some-password")
        removeAndSet(config.BandageConfigItem.USER_ONE_FULL_NAME, "some-user-1")
        removeAndSet(config.BandageConfigItem.USER_TWO_FULL_NAME, "some-user-2")
        removeAndSet(config.BandageConfigItem.USER_THREE_FULL_NAME, "some-user-3")
    }.toMap()

    @Suppress("UNCHECKED_CAST")
    return config.Configuration(configValues as Map<RequiredConfigItem, String>, config.BandageConfig, null)
}
