package org.gameyfin.pluginapi.core.config

import java.io.Serializable

/**
 * Alias for a list of ConfigMetadata objects for plugin configuration.
 */
typealias PluginConfigMetadata = List<ConfigMetadata<*>>

/**
 * Represents metadata for a configuration property.
 *
 * @param T The type of the configuration value, must be Serializable.
 * @property key The unique (in the scope of the plugin) key for the configuration property.
 * @property type The class type of the configuration value.
 * @property label A human-readable label for the configuration property.
 * @property description A short description of the configuration property.
 * @property default The default value for the configuration property, if any.
 * @property isSecret Whether the configuration value is secret (e.g., password). Affects how the value is displayed in the UI.
 * @property isRequired Whether the configuration property is required.
 * @property allowedValues The allowed values for the configuration property, if applicable (e.g., for enums). Will be populated automatically if the type is an enum.
 */
data class ConfigMetadata<T : Serializable>(
    val key: String,
    val type: Class<T>,
    val label: String,
    val description: String,
    val default: T? = null,
    val isSecret: Boolean = false,
    val isRequired: Boolean = true,
) {
    /**
     * List of allowed values for the configuration property, if the type is an enum.
     */
    var allowedValues: List<T>? = null

    init {
        allowedValues = type.enumConstants?.toList()
    }
}