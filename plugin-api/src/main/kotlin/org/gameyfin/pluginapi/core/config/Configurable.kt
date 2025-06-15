package org.gameyfin.pluginapi.core.config

import java.io.Serializable

/**
 * Interface for classes that can be configured using plugin configuration metadata.
 * Provides methods for loading, validating, and accessing configuration values.
 */
interface Configurable {
    /**
     * The metadata describing the configuration options for this configurable instance.
     */
    val configMetadata: PluginConfigMetadata

    /**
     * Loads configuration values from the provided map.
     *
     * @param config A map of configuration keys to their string values (values are nullable).
     */
    fun loadConfig(config: Map<String, String?>)

    /**
     * Validates the current configuration state.
     *
     * @return The result of the configuration validation.
     */
    fun validateConfig(): PluginConfigValidationResult

    /**
     * Validates the provided configuration map.
     *
     * @param config A map of configuration keys to their string values (nullable).
     * @return The result of the configuration validation.
     */
    fun validateConfig(config: Map<String, String?>): PluginConfigValidationResult

    /**
     * Retrieves a required configuration value by key.
     *
     * @param key The configuration key.
     * @return The configuration value of type T.
     * @throws Exception if the key is missing or the value is invalid.
     */
    fun <T : Serializable> config(key: String): T

    /**
     * Retrieves an optional configuration value by key.
     *
     * @param key The configuration key.
     * @return The configuration value of type T, or null if not present.
     */
    fun <T : Serializable> optionalConfig(key: String): T?
}