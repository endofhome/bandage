package config

fun dummyConfiguration(): Configuration {
    val baseConfigValues = BandageConfig.values().associate { it to "unset" }.toMutableMap()
    val configValues: Map<BandageConfigItem, String> = baseConfigValues.apply {
        removeAndSet(BandageConfigItem.API_KEY, "some-key")
        removeAndSet(BandageConfigItem.PASSWORD, "some-password")
        removeAndSet(BandageConfigItem.USER_ONE_FULL_NAME, "someone 1")
        removeAndSet(BandageConfigItem.USER_TWO_FULL_NAME, "someone 2")
        removeAndSet(BandageConfigItem.USER_THREE_FULL_NAME, "someone 3")
    }.toMap()

    @Suppress("UNCHECKED_CAST")
    return Configuration(configValues as Map<RequiredConfigItem, String>, BandageConfig, null)
}
