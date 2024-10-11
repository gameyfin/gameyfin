package de.grimsi.gameyfin.pluginapi.gamemetadata

import org.pf4j.ExtensionPoint

interface GameMetadataFetcher : ExtensionPoint {
    fun getConfig(): Map<String, String>
    fun setConfig(config: Map<String, String>)
    fun fetchMetadata(gameId: String): GameMetadata
}