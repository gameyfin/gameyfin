package org.gameyfin.plugins.store.gogdb

import org.gameyfin.pluginapi.core.wrapper.GameyfinPlugin
import org.gameyfin.pluginapi.core.wrapper.PluginContext

class GogDbPlugin(context: PluginContext) : GameyfinPlugin(context) {

    override fun stop() {
        JdkGogDbHttp.shutdown()
    }
}
