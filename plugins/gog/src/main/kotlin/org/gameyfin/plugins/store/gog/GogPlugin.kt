package org.gameyfin.plugins.store.gog

import org.gameyfin.pluginapi.core.wrapper.GameyfinPlugin
import org.gameyfin.pluginapi.core.wrapper.PluginContext

class GogPlugin(context: PluginContext) : GameyfinPlugin(context) {

    override fun stop() {
        JdkGogHttp.shutdown()
    }
}
