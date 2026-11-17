package org.gameyfin.plugins.store.steamcmd

import org.gameyfin.pluginapi.core.wrapper.GameyfinPlugin
import org.gameyfin.pluginapi.core.wrapper.PluginContext

class SteamCmdPlugin(context: PluginContext) : GameyfinPlugin(context) {

    override fun stop() {
        JdkSteamCmdHttp.shutdown()
    }
}
