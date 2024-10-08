package de.grimsi.gameyfin.pluginapi.gamemetadata

interface GameMetadataPlugin {
    fun getConfig(): Map<String, String>
    fun setConfig(config: Map<String, String>)
    fun fetchMetadata(gameId: String): GameMetadata
}