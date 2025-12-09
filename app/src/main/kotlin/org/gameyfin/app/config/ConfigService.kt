package org.gameyfin.app.config

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.config.dto.ConfigEntryDto
import org.gameyfin.app.config.dto.ConfigUpdateDto
import org.gameyfin.app.config.entities.ConfigEntry
import org.gameyfin.app.config.persistence.ConfigRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.io.Serializable
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

@Service
class ConfigService(
    private val appConfigRepository: ConfigRepository,
    private val objectMapper: ObjectMapper
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

        log.trace { "Getting config value '${configProperty.key}'" }

        val appConfig = appConfigRepository.findByIdOrNull(configProperty.key)
        return if (appConfig != null) {
            deserializeValue(appConfig.value, configProperty)
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

        log.trace { "Getting config value '$key'" }

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
                description = configProperty.description,
                elementType = configProperty.type.java.componentType?.simpleName,
                allowedValues = configProperty.allowedValues?.map { it.toString() },
                min = configProperty.min,
                max = configProperty.max,
                step = configProperty.step
            )
        }
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

        val serializedValue = serializeValue(value, key)

        if (configEntry == null) {
            configEntry = ConfigEntry(configProperty.key, serializedValue)
        } else {
            configEntry.value = serializedValue
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
     * Deserialize a value from the database to its proper type.
     *
     * @param value: The serialized value from the database
     * @param configProperty: The config property containing type information
     * @return The deserialized value
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Serializable> deserializeValue(value: Serializable, configProperty: ConfigProperties<T>): T {
        return try {
            val typeReference = objectMapper.typeFactory.constructType(configProperty.type.java)
            objectMapper.readValue(value.toString(), typeReference) as T
        } catch (e: JsonProcessingException) {
            throw IllegalArgumentException(
                "Failed to deserialize value '$value' for key '${configProperty.key}' to type ${configProperty.type.simpleName}: ${e.message}",
                e
            )
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "Failed to deserialize value '$value' for key '${configProperty.key}' to type ${configProperty.type.simpleName}: ${e.message}",
                e
            )
        }
    }

    /**
     * Serialize a value to be stored in the database.
     *
     * @param value: The value to serialize
     * @param key: The config key (for error messages)
     * @return The serialized value as a string
     */
    private fun <T : Serializable> serializeValue(value: T, key: String): String {
        return try {
            objectMapper.writeValueAsString(value)
        } catch (e: JsonProcessingException) {
            throw IllegalArgumentException(
                "Failed to serialize value for key '$key': ${e.message}",
                e
            )
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

