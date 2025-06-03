package de.grimsi.gameyfin.pluginapi.core.config

import java.io.Serializable

interface Configurable {
    val configMetadata: PluginConfigMetadata

    fun loadConfig(config: Map<String, String?>)

    fun validateConfig(): PluginConfigValidationResult
    fun validateConfig(config: Map<String, String?>): PluginConfigValidationResult

    fun <T : Serializable> config(key: String): T
    fun <T : Serializable> optionalConfig(key: String): T?
}