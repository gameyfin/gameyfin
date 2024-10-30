package de.grimsi.gameyfin.plugins.igdb

import com.api.igdb.request.IGDBWrapper
import com.api.igdb.request.TwitchAuthenticator
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadata
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadataFetcher
import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import java.time.Instant

class IgdbPlugin(wrapper: PluginWrapper) : Plugin(wrapper) {

    override fun start() {
        authenticate()
    }

    override fun stop() {
        println("IgdbPlugin.stop()")
    }

    private fun authenticate() {
        // Kotlin example
        val token = TwitchAuthenticator.requestTwitchToken("CLIENT_ID", "CLIENT_SECRET")
        if (token == null) {
            println("Failed to authenticate with Twitch")
            return
        }
        IGDBWrapper.setCredentials("client_id", token.access_token)
    }

    @Extension
    class IgdbMetadataFetcher : GameMetadataFetcher {
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
}