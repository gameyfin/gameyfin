package de.grimsi.gameyfin.pluginapi.gamemetadata

import org.pf4j.ExtensionPoint

interface GameMetadataProvider : ExtensionPoint {
    fun fetchMetadata(gameId: String, maxResults: Int = 1): List<GameMetadata>
}