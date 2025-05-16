package de.grimsi.gameyfin.pluginapi.core

import org.pf4j.PluginWrapper

abstract class ConfigurableGameyfinPlugin(wrapper: PluginWrapper) : GameyfinPlugin(wrapper), Configurable {

    companion object {
        lateinit var plugin: ConfigurableGameyfinPlugin
            private set
    }

    init {
        plugin = this
    }

    override var config: Map<String, String?> = emptyMap()
}