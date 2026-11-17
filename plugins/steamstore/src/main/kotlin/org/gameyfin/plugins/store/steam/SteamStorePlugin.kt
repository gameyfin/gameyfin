package org.gameyfin.plugins.store.steam

import org.gameyfin.pluginapi.core.wrapper.GameyfinPlugin
import org.gameyfin.pluginapi.core.wrapper.PluginContext

class SteamStorePlugin(context: PluginContext) : GameyfinPlugin(context) {

    override fun stop() {
        JdkSteamStoreHttp.shutdown()
    }
}
