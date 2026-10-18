package org.gameyfin.pluginapi.core.config

/**
 * Interface for plugins that can be configured using plugin configuration metadata.
 * Provides the methods Gameyfin uses to describe, load and validate the configuration of a plugin.
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
     * Validates the provided configuration map.
     *
     * @param config A map of configuration keys to their string values (nullable).
     * @return The result of the configuration validation.
     */
    fun validateConfig(config: Map<String, String?>): PluginConfigValidationResult
}
