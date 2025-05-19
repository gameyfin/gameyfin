package de.grimsi.gameyfin.pluginapi.core

interface Configurable {
    val configMetadata: List<PluginConfigElement>
    var config: Map<String, String?>

    fun validateConfig(): PluginConfigValidationResult = validateConfig(config)
    fun validateConfig(config: Map<String, String?>): PluginConfigValidationResult
}