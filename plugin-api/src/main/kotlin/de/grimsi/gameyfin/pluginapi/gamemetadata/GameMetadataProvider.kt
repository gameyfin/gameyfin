package de.grimsi.gameyfin.pluginapi.gamemetadata

import org.pf4j.ExtensionPoint

interface GameMetadataProvider : ExtensionPoint {
    fun fetchByTitle(gameTitle: String, maxResults: Int = 1): List<GameMetadata>

    fun fetchById(id: String): GameMetadata?
}