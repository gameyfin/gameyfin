package org.gameyfin.plugins.store.epic

import org.gameyfin.pluginapi.core.wrapper.GameyfinPlugin
import org.gameyfin.pluginapi.core.wrapper.PluginContext
import org.gameyfin.pluginapi.gamestore.GameStoreDlc
import org.gameyfin.pluginapi.gamestore.GameStoreInfo
import org.gameyfin.pluginapi.gamestore.GameStoreProduct
import org.gameyfin.pluginapi.gamestore.GameStoreProvider
import org.gameyfin.pluginapi.gamestore.GameStoreVersion
import org.pf4j.Extension

class EpicPlugin(context: PluginContext) : GameyfinPlugin(context) {

    companion object {
        private var http: EpicHttp? = null

        /** Shared HTTP client, (re)created on demand and closed in [stop] when the plugin is stopped or reloaded. */
        @Synchronized
        fun http(): EpicHttp = http ?: JavaEpicHttp().also { http = it }

        @Synchronized
        private fun closeHttp() {
            http?.close()
            http = null
        }
    }

    override fun stop() {
        closeHttp()
    }

    @Suppress("Unused")
    @Extension
    class EpicStoreProvider : GameStoreProvider {

        private val client get() = EpicClient(http())

        override val store: GameStoreInfo = EPIC_STORE

        override fun search(query: String): List<GameStoreProduct> = client.search(query)

        override fun fetchVersions(productId: String): List<GameStoreVersion> = client.fetchVersions(productId)

        override fun fetchDlc(productId: String): List<GameStoreDlc> = client.fetchDlc(productId)
    }
}
