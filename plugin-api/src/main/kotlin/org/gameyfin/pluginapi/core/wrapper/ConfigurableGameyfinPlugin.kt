package org.gameyfin.pluginapi.core.wrapper

import org.gameyfin.pluginapi.core.config.ConfigMetadata
import org.gameyfin.pluginapi.core.config.Configurable
import org.gameyfin.pluginapi.core.config.PluginConfigError
import org.gameyfin.pluginapi.core.config.PluginConfigValidationResult
import org.pf4j.PluginWrapper
import java.io.Serializable

/**
 * Abstract base class for Gameyfin plugins that support configuration.
 *
 * This class implements the [Configurable] interface and provides default logic for loading,
 * validating, and accessing plugin configuration values using metadata.
 *
 * @constructor Creates a configurable Gameyfin plugin with the given [PluginWrapper].
 * @param wrapper The plugin wrapper provided by the Gameyfin application.
 */
@Suppress("UNCHECKED_CAST")
abstract class ConfigurableGameyfinPlugin(wrapper: PluginWrapper) : GameyfinPlugin(wrapper), Configurable {

    /**
     * The current configuration map, where keys are configuration property names and values are their string representations.
     */
    private var config: Map<String, String?> = emptyMap()

    /**
     * Loads configuration values from the provided map.
     *
     * @param config A map of configuration keys to their string values (nullable).
     */
    override fun loadConfig(config: Map<String, String?>) {
        this.config = config
    }

    /**
     * Validates the current configuration state.
     *
     * @return The result of the configuration validation.
     */
    override fun validateConfig(): PluginConfigValidationResult = validateConfig(config)

    /**
     * Validates the provided configuration map.
     * The validation covers basic checks such as required fields, type casting, and allowed values.
     * It's recommended to override this method in subclasses to implement additional validation logic specific to the plugin.
     *
     * @param config A map of configuration keys to their string values (nullable).
     * @return The result of the configuration validation.
     */
    override fun validateConfig(config: Map<String, String?>): PluginConfigValidationResult {
        val errors = mutableMapOf<String, String>()

        for (meta in configMetadata) {
            val value = resolveValue(meta.key, config)
            if (meta.isRequired && value == null) {
                errors[meta.key] = "${meta.label} is required"
                continue
            }
            if (value != null) {
                try {
                    castConfigValue(meta, value)
                } catch (e: PluginConfigError) {
                    errors[meta.key] = e.message ?: "Invalid value"
                }
            }
        }

        return if (errors.isEmpty()) {
            PluginConfigValidationResult.VALID
        } else {
            PluginConfigValidationResult.INVALID(errors)
        }
    }

    /**
     * Retrieves an optional configuration value by key.
     *
     * @param key The configuration key.
     * @return The configuration value of type T, or null if not present or invalid.
     * @throws PluginConfigError if the value cannot be cast to the expected type.
     */
    override fun <T : Serializable> optionalConfig(key: String): T? {
        val meta = resolveMetadata(key)
        val value = resolveValue(key) ?: return null

        return try {
            castConfigValue(meta, value) as T
        } catch (e: Exception) {
            throw PluginConfigError("Failed to cast value for key '$key' to type ${meta.type.simpleName}: ${e.message}")
        }
    }

    /**
     * Retrieves a required configuration value by key.
     *
     * @param key The configuration key.
     * @return The configuration value of type T.
     * @throws PluginConfigError if the key is missing or the value is invalid.
     */
    override fun <T : Serializable> config(key: String): T {
        val value = optionalConfig<T>(key)
            ?: throw PluginConfigError("Required configuration key '$key' is missing or has no value")
        return value
    }

    /**
     * Casts a configuration value to the expected type defined in the metadata.
     *
     * Handles enums, common primitive types, and attempts to use valueOf/parse methods via reflection for custom types.
     *
     * @param meta The configuration metadata describing the expected type.
     * @param value The value to cast.
     * @return The cast value, or throws PluginConfigError if casting fails.
     * @throws PluginConfigError if the value cannot be cast to the expected type.
     */
    private fun castConfigValue(meta: ConfigMetadata<*>, value: Any): Any? {
        val expectedType = meta.type

        // Handle enums
        if (expectedType.isEnum) {
            try {
                return java.lang.Enum.valueOf(expectedType as Class<out Enum<*>>, value.toString())
            } catch (_: IllegalArgumentException) {
                throw PluginConfigError("Invalid value '${value}', must be one of ${meta.allowedValues!!.joinToString(", ")}")
            }
        }

        // If already correct type
        if (expectedType.isInstance(value)) return value

        // Try to convert common types
        try {
            return when (expectedType) {
                Int::class.java, Integer::class.java -> value.toString().toInt()
                Float::class.java, java.lang.Float::class.java -> value.toString().toFloat()
                Double::class.java, java.lang.Double::class.java -> value.toString().toDouble()
                Long::class.java, java.lang.Long::class.java -> value.toString().toLong()
                Boolean::class.java, java.lang.Boolean::class.java -> value.toString().toBooleanStrict()
                String::class.java -> value.toString()
                else -> {
                    // Try valueOf(String) or parse(String) via reflection
                    val method = expectedType.methods.find {
                        (it.name == "valueOf" || it.name == "parse") &&
                                it.parameterTypes.size == 1 &&
                                it.parameterTypes[0] == String::class.java
                    }
                    if (method != null) {
                        method.invoke(null, value.toString())
                    } else {
                        throw IllegalArgumentException()
                    }
                }
            }
        } catch (_: Exception) {
            throw PluginConfigError("Value must be of type ${expectedType.simpleName}")
        }
    }

    /**
     * Resolves the configuration metadata for a given key.
     *
     * @param key The configuration key to look up.
     * @return The corresponding ConfigMetadata instance.
     * @throws PluginConfigError if the key is unknown.
     */
    private fun resolveMetadata(key: String): ConfigMetadata<*> {
        return configMetadata.find { it.key == key }
            ?: throw PluginConfigError("Unknown configuration key: $key")
    }

    /**
     * Resolves the value for a configuration key, optionally using an override map.
     *
     * @param key The configuration key to resolve.
     * @param configOverride An optional map to override the current configuration.
     * @return The resolved value, or the default from metadata if not present.
     */
    private fun resolveValue(key: String, configOverride: Map<String, Serializable?>? = null): Serializable? {
        val meta = resolveMetadata(key)
        val conf = configOverride ?: config
        return conf[key] ?: meta.default
    }
}