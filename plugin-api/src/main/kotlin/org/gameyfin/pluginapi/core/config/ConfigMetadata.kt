package org.gameyfin.pluginapi.core.config

import java.io.Serializable

typealias PluginConfigMetadata = List<ConfigMetadata<*>>

data class ConfigMetadata<T : Serializable>(
    val key: String,
    val type: Class<T>,
    val label: String,
    val description: String,
    val default: T? = null,
    val isSecret: Boolean = false,
    val isRequired: Boolean = true,
) {
    var allowedValues: List<T>? = null

    init {
        allowedValues = type.enumConstants?.toList()
    }
}