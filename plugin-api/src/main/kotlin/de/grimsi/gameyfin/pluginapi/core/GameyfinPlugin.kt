package de.grimsi.gameyfin.pluginapi.core

interface GameyfinPlugin {
    fun getConfigMetadata(): List<PluginConfigElement>
    fun getCurrentConfig(): Map<String, String?>
    fun loadConfig(config: Map<String, String?>)
}