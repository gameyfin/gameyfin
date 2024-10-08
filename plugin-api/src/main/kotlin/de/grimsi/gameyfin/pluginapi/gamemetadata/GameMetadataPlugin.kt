package de.grimsi.gameyfin.pluginapi.gamemetadata

import org.pf4j.ExtensionPoint

interface GameMetadataPlugin : ExtensionPoint {
    fun getConfig(): Map<String, String>
    fun setConfig(config: Map<String, String>)
    fun fetchMetadata(gameId: String): GameMetadata
}