package de.grimsi.gameyfin.config

import de.grimsi.gameyfin.config.dto.ConfigEntryDto
import de.grimsi.gameyfin.config.entities.ConfigEntry
import de.grimsi.gameyfin.config.persistence.ConfigRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.io.Serializable

@Service
@Transactional
class ConfigService(
    private val appConfigRepository: ConfigRepository
) {
    private val log = KotlinLogging.logger {}

    /**
     * Get all known config values.
     *
     * @param prefix: Optional prefix to filter the config values
     * @return A map of all config values
     */
    fun getAllConfigValues(prefix: String?): List<ConfigEntryDto> {

        log.info { "Getting all config values for prefix '$prefix'" }

        var configProperties = ConfigProperties::class.sealedSubclasses.flatMap { subclass ->
            subclass.objectInstance?.let { listOf(it) } ?: listOf()
        }

        if (prefix != null) {
            configProperties = configProperties.filter { it.key.startsWith(prefix) }
        }

        return configProperties.map { configProperty ->
            val appConfig = appConfigRepository.findById(configProperty.key).orElse(null)
            ConfigEntryDto(
                key = configProperty.key,
                value = appConfig?.value ?: configProperty.default?.toString(),
                defaultValue = configProperty.default?.toString(),
                type = configProperty.type.simpleName ?: "Unknown",
                description = configProperty.description
            )
        }
    }

    /**
     * Get the current value of a config property in a type-safe way.
     * Used internally.
     *
     * @param configProperty: The config property containing necessary type information
     * @return The current value if set or the default value
     * @throws IllegalArgumentException if no value is set and no default value exists
     */
    fun <T : Serializable> getConfigValue(configProperty: ConfigProperties<T>): T {

        log.info { "Getting config value '${configProperty.key}'" }

        val appConfig = appConfigRepository.findById(configProperty.key).orElse(null)
        return if (appConfig != null) {
            getValue(appConfig.value, configProperty)
        } else {
            configProperty.default ?: throw IllegalArgumentException("No value found for key: ${configProperty.key}")
        }
    }

    /**
     * Get the current value of a config property in a *not* type-safe way.
     * Used for the external API.
     *
     * @param key: The key of the config property
     * @return The current value if set or the default value
     * @throws IllegalArgumentException if no value is set and no default value exists
     */
    fun getConfigValue(key: String): String {

        log.info { "Getting config value '$key'" }

        val configProperty = findConfigProperty(key)
        val appConfig = appConfigRepository.findById(configProperty.key).orElse(null)

        return if (appConfig != null) {
            getValue(appConfig.value, configProperty).toString()
        } else {
            configProperty.default?.toString()
                ?: throw IllegalArgumentException("No value found for key: ${configProperty.key}")
        }
    }

    /**
     * Set the value for a specified key.
     * Checks if the value can be cast to the type defined for the config property.
     *
     * @param key: Key of the target config property
     * @param value: Value to set the config property to
     * @throws IllegalArgumentException if the value can't be cast to the type defined for the config property
     */
    fun <T : Serializable> setConfigValue(key: String, value: T) {

        log.info { "Set config value '$key' to '$value'" }

        val configKey = findConfigProperty(key)

        // Check if the value can be cast to the type defined for the config property
        val castedValue = getValue(value.toString(), configKey)

        var configEntry = appConfigRepository.findById(key).orElse(null)

        if (configEntry == null) {
            configEntry = ConfigEntry(configKey.key, castedValue.toString())
        } else {
            configEntry.value = castedValue.toString()
        }

        appConfigRepository.save(configEntry)
    }

    /**
     * Reset a given config property to its default value if it has a default value.
     * Otherwise, delete the config key from the database.
     *
     * @param key: Key of the config property
     */
    fun resetConfigValue(key: String) {

        log.info { "Reset config value '$key'" }

        val configKey = findConfigProperty(key)

        if (configKey.default == null) {
            deleteConfig(key)
            return
        }

        val appConfig = appConfigRepository.findById(configKey.key).orElse(null)
        if (appConfig != null) {
            appConfig.value = configKey.default.toString()
            appConfigRepository.save(appConfig)
        }
    }

    /**
     * Remove a config property from the database
     *
     * @param key: Key of the config property
     */
    fun deleteConfig(key: String) {

        log.info { "Delete config value '$key'" }

        val configKey = findConfigProperty(key)
        appConfigRepository.deleteById(configKey.key)
    }

    /**
     * Get the value of the config property in a type-safe way.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Serializable> getValue(value: String, configProperty: ConfigProperties<T>): T {
        return when (configProperty.type) {
            String::class -> value as T
            Boolean::class -> value.toBoolean() as T
            Int::class -> value.toFloat().toInt() as T
            Float::class -> value.toFloat() as T
            Enum::class -> value as T
            else -> {
                throw RuntimeException("Unknown config type ${configProperty.type}: '$value' for key ${configProperty.key}")
            }
        }
    }

    /**
     * Returns a config property
     */
    private fun findConfigProperty(key: String): ConfigProperties<*> {
        // Use reflection to get all objects defined within ConfigKey
        val configProperties = ConfigProperties::class.sealedSubclasses.flatMap { subclass ->
            subclass.objectInstance?.let { listOf(it) } ?: listOf()
        }

        // Find the matching config key based on the string key
        return configProperties.find { it.key == key }
            ?: throw IllegalArgumentException("Unknown configuration key: $key")
    }
}

