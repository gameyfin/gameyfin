package de.grimsi.gameyfin.pluginapi.gamemetadata

import org.pf4j.ExtensionPoint

interface GameMetadataFetcher : ExtensionPoint {
    fun fetchMetadata(gameId: String): GameMetadata
}