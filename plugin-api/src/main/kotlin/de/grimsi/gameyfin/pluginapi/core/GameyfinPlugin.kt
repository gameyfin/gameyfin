package de.grimsi.gameyfin.pluginapi.core

import org.pf4j.Plugin
import org.pf4j.PluginWrapper

abstract class GameyfinPlugin(wrapper: PluginWrapper) : Plugin(wrapper) {

    abstract val configMetadata: List<PluginConfigElement>
    protected open var config: Map<String, String?> = emptyMap()

    open fun getCurrentConfig(): Map<String, String?> {
        return config
    }

    open fun loadConfig(config: Map<String, String?>) {
        this.config = config
    }
}