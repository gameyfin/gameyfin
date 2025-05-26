package de.grimsi.gameyfin.config

import de.grimsi.gameyfin.config.dto.ConfigEntryDto
import de.grimsi.gameyfin.config.dto.ConfigUpdateDto
import de.grimsi.gameyfin.config.entities.ConfigEntry
import de.grimsi.gameyfin.config.persistence.ConfigRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.io.Serializable
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

@Service
class ConfigService(
    private val appConfigRepository: ConfigRepository
) {
    companion object {
        private val log = KotlinLogging.logger {}

        /* Websockets */
        private val configUpdates = Sinks.many().multicast().onBackpressureBuffer<ConfigUpdateDto>(1024, false)

        fun subscribe(): Flux<List<ConfigUpdateDto>> {
            log.debug { "New subscription for configUpdates (#${configUpdates.currentSubscriberCount()})" }
            return configUpdates.asFlux()
                .buffer(100.milliseconds.toJavaDuration())
                .doOnSubscribe { log.debug { "Subscriber added to configUpdates [${configUpdates.currentSubscriberCount()}]" } }
                .doFinally {
                    log.debug { "Subscriber removed from configUpdates with signal type $it [${configUpdates.currentSubscriberCount()}]" }
                }
        }

        fun emit(update: ConfigUpdateDto) {
            configUpdates.tryEmitNext(update)
        }
    }

    /**
     * Get the current value of a config property in a type-safe way.
     *
     * @param configProperty: The config property containing necessary type information
     * @return The current value if set or the default value or null if no value is set and no default value exists
     */
    fun <T : Serializable> get(configProperty: ConfigProperties<T>): T? {

        log.debug { "Getting config value '${configProperty.key}'" }

        val appConfig = appConfigRepository.findByIdOrNull(configProperty.key)
        return if (appConfig != null) {
            getValue(appConfig.value, configProperty)
        } else {
            configProperty.default ?: return null
        }
    }

    /**
     * Get the current value of a config property in a *not* type-safe way.
     * Used for the external API.
     *
     * @param key: The key of the config property
     * @return The current value if set or the default value or null if no value is set and no default value exists
     */
    fun get(key: String): Serializable? {

        log.debug { "Getting config value '$key'" }

        val configProperty = findConfigProperty(key)

        return get(configProperty)
    }

    /**
     * Get all known config values.
     *
     * @return A map of all config values
     */
    fun getAll(): List<ConfigEntryDto> {

        log.debug { "Getting all config values" }

        val configProperties = ConfigProperties::class.sealedSubclasses.flatMap { subclass ->
            subclass.objectInstance?.let { listOf(it) } ?: listOf()
        }

        return configProperties.map { configProperty ->
            ConfigEntryDto(
                key = configProperty.key,
                value = get(configProperty),
                defaultValue = configProperty.default,
                type = configProperty.type.simpleName ?: "Unknown",
                elementType = configProperty.type.java.componentType?.simpleName,
                allowedValues = configProperty.allowedValues?.map { it.toString() },
                description = configProperty.description
            )
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
    @Suppress("UNCHECKED_CAST")
    fun <T : Serializable> set(key: String, value: T) {
        log.debug { "Set config value '$key'" }

        val configProperty = findConfigProperty(key)

        var configEntry = appConfigRepository.findByIdOrNull(key)

        val parsedValue =
            if (value.javaClass.isArray) {
                (value as Array<Serializable>).joinToString(",")
            } else
                value.toString()

        if (configEntry == null) {
            configEntry = ConfigEntry(configProperty.key, parsedValue)
        } else {
            configEntry.value = parsedValue
        }

        appConfigRepository.save(configEntry)
    }

    /**
     * Set multiple config values at once.
     * Configs with a null value will be deleted.
     *
     * @param update: A [ConfigUpdateDto] containing a map of key-value pairs to set
     */
    fun update(update: ConfigUpdateDto) {
        update.updates.forEach { (key, value) ->
            if (value == null) {
                delete(key)
            } else {
                set(key, value)
            }
        }
        emit(update)
    }

    /**
     * Set the value for a specified key in a type-safe way.
     *
     * @param configProperty: The target config property
     * @param value: Value to set the config property to
     * @throws IllegalArgumentException if the value can't be cast to the type defined for the config property
     */
    fun <T : Serializable> set(configProperty: ConfigProperties<T>, value: T) {
        return set(configProperty.key, value)
    }

    /**
     * Remove a config property from the database.
     * This will also cause it to reset to its default value.
     *
     * @param key: Key of the config property
     */
    fun delete(key: String) {

        log.debug { "Delete config value '$key'" }

        val configKey = findConfigProperty(key)
        appConfigRepository.deleteById(configKey.key)
    }

    /**
     * Get the value of the config property in a type-safe way.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Serializable> getValue(value: Serializable, configProperty: ConfigProperties<T>): T {
        val value = value.toString()
        return when {
            configProperty.type == String::class -> value as T
            configProperty.type == Boolean::class -> value.toBoolean() as T
            configProperty.type == Int::class -> value.toFloat().toInt() as T
            configProperty.type == Float::class -> value.toFloat() as T

            configProperty.type.java.isEnum -> {
                val enumConstants = configProperty.type.java.enumConstants
                enumConstants.firstOrNull { it.toString() == value }
                    ?: throw IllegalArgumentException("Unknown enum value '$value' for key ${configProperty.key}")
            }

            configProperty.type.java.isArray -> {
                val componentType = configProperty.type.java.componentType
                // Remove the brackets and split the string by commas
                val elements = value
                    .removeSurrounding("[", "]")
                    .split(",")
                    .filter { it.isNotBlank() }

                when (componentType) {
                    String::class.java -> elements.toTypedArray() as T
                    Boolean::class.java -> elements.map { it.toBoolean() }.toTypedArray() as T
                    Int::class.java -> elements.map { it.toInt() }.toTypedArray() as T
                    Float::class.java -> elements.map { it.toFloat() }.toTypedArray() as T
                    else -> throw IllegalArgumentException("Unsupported array type: ${componentType.name}")
                }
            }

            else -> throw IllegalArgumentException("Unknown config type ${configProperty.type}: '$value' for key ${configProperty.key}")
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

