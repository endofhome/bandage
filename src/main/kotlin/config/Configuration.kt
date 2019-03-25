package config

import java.nio.file.Path

data class Configuration(private val config: Map<RequiredConfigItem, String?>, val requiredConfig: RequiredConfig, val configDir: Path?) {

    init {
        validate(requiredConfig.values().toSet(), config)
    }

    fun get(item: RequiredConfigItem): String = try {
        this.config.filter { it.key.name == item.name }.values.first()!!
    } catch (e: Exception) {
        throw ConfigurationException("${item.name} was not available during get")
    }

    private fun validate(required: Set<RequiredConfigItem>, provided: Map<RequiredConfigItem, String?>) {
        fun pluralise(missingConfig: List<RequiredConfigItem>): String = when {
            missingConfig.size > 1 -> "s"
            else                   -> ""
        }

        if (required.map { it.name }.sorted() != provided.keys.map { it.name }.sorted() || provided.values.contains(null)) {
            val completelyMissing  = required.filter { provided.map { it.key.name }.contains(it.name).not() }
            val nullValues = provided.filter { it.value == null }.keys
            val missingConfig = completelyMissing + nullValues
            throw ConfigurationException(
                "Config value${pluralise(missingConfig)} required but not found:".newlines(2) +
                        missingConfig.joinToString(osNewline) { it.name }.newlines(1)
            )
        }
    }
}

val osNewline: String = System.getProperty("line.separator")
fun String.newlines(numberOf: Int) = this + (1..numberOf).map { osNewline }.joinToString("")
class ConfigurationException(override val message: String) : RuntimeException()
