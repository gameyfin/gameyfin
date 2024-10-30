package de.grimsi.gameyfin.plugins.igdb

import com.api.igdb.request.IGDBWrapper
import com.api.igdb.request.TwitchAuthenticator
import de.grimsi.gameyfin.pluginapi.core.PluginConfigError
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadata
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadataFetcher
import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import java.time.Instant

class IgdbPlugin(wrapper: PluginWrapper) : Plugin(wrapper) {

    companion object {
        val config: IgdbPluginConfig = IgdbPluginConfig(null, null)
    }

    override fun start() {
        authenticate()
    }

    override fun stop() {
        log.debug("IgdbPlugin.stop()")
    }

    private fun authenticate() {
        log.debug("Authenticating on Twitch API...")

        // FIXME: This should be read from the config
        val clientId = "8nrnjn74x1oa7of2g8sg4voy2lapml"
        // FIXME: This should be read from the config
        val clientSecret = "pyrvg3sdduxjg4qxidra9237xj17yn"

        // val clientId: String = config.clientId ?: throw PluginConfigError("Twitch Client ID not set")
        // val clientSecret: String = config.clientSecret ?: throw PluginConfigError("Twitch Client Secret not set")

        val token = TwitchAuthenticator.requestTwitchToken(clientId, clientSecret)
            ?: throw PluginConfigError("Failed to authenticate on Twitch API")

        IGDBWrapper.setCredentials(clientId, token.access_token)

        log.debug("Authentication successful")
    }

    @Extension
    class IgdbMetadataFetcher : GameMetadataFetcher {
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