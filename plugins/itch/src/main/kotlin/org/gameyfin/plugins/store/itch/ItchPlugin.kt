package org.gameyfin.plugins.store.itch

import org.gameyfin.pluginapi.core.wrapper.GameyfinPlugin
import org.gameyfin.pluginapi.core.wrapper.PluginContext

class ItchPlugin(context: PluginContext) : GameyfinPlugin(context) {

    override fun stop() {
        JdkItchHttp.shutdown()
    }
}
