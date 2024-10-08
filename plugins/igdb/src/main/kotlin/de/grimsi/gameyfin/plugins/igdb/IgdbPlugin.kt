package de.grimsi.gameyfin.plugins.igdb

import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadata
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadataPlugin
import org.pf4j.Extension
import java.time.Instant

@Extension
class IgdbPlugin : GameMetadataPlugin {
    override fun getConfig(): Map<String, String> {
        TODO("Not yet implemented")
    }

    override fun setConfig(config: Map<String, String>) {
        TODO("Not yet implemented")
    }

    override fun fetchMetadata(gameId: String): GameMetadata {
        return GameMetadata(
            title = "Test Game",
            description = "This is a test game",
            release = Instant.now(),
            userRating = 0,
            criticRating = 0,
            developedBy = listOf("Test Developer"),
            publishedBy = listOf("Test Publisher"),
            genres = listOf(),
            themes = listOf(),
            screenshotUrls = listOf(),
            videoUrls = listOf(),
            features = listOf(),
            perspectives = listOf()
        )
    }
}