package de.grimsi.gameyfin.pluginapi.core

import org.pf4j.RuntimeMode

class PluginContext(private val runtimeMode: RuntimeMode) {
    fun getRuntimeMode(): RuntimeMode {
        return runtimeMode
    }
}