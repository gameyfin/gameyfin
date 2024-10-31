package de.grimsi.gameyfin.plugins.igdb

import com.api.igdb.apicalypse.APICalypse
import com.api.igdb.request.IGDBWrapper
import com.api.igdb.request.TwitchAuthenticator
import com.api.igdb.request.games
import de.grimsi.gameyfin.pluginapi.core.GameyfinPlugin
import de.grimsi.gameyfin.pluginapi.core.PluginConfigElement
import de.grimsi.gameyfin.pluginapi.core.PluginConfigError
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadata
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadataFetcher
import org.pf4j.Extension
import org.pf4j.PluginWrapper
import java.time.Instant
import kotlin.collections.filter

class IgdbPlugin(wrapper: PluginWrapper) : GameyfinPlugin(wrapper) {

    override val configMetadata: List<PluginConfigElement> = listOf(
        PluginConfigElement("clientId", "Twitch client ID", "Your Twitch Client ID"),
        PluginConfigElement("clientSecret", "Twitch client secret", "Your Twitch Client Secret")
    )

    override fun start() {
        try {
            authenticate()
        } catch (e: PluginConfigError) {
            log.error(e.message)
        }
    }

    override fun stop() {
        log.debug("IgdbPlugin.stop()")
    }

    private fun authenticate() {
        log.debug("Authenticating on Twitch API...")

        val clientId: String = config["clientId"] ?: throw PluginConfigError("Twitch Client ID not set")
        val clientSecret: String = config["clientSecret"] ?: throw PluginConfigError("Twitch Client Secret not set")

        val token = TwitchAuthenticator.requestTwitchToken(clientId, clientSecret)
            ?: throw PluginConfigError("Failed to authenticate on Twitch API with provided credentials")

        IGDBWrapper.setCredentials(clientId, token.access_token)

        log.debug("Authentication successful")
    }

    @Extension
    class IgdbMetadataFetcher : GameMetadataFetcher {
        override fun fetchMetadata(gameId: String): GameMetadata {
            val findGameByName = APICalypse()
                .fields("*")
                .limit(100)
                .search(gameId)

            val game = IGDBWrapper.games(findGameByName).filter { it.slug == gameId.lowercase() }.firstOrNull()
                ?: throw IllegalArgumentException("Could not match game with ID '$gameId'")

            return GameMetadata(
                title = game.name,
                description = game.summary,
                release = Instant.ofEpochSecond(game.firstReleaseDate.seconds),
                userRating = game.rating.toInt(),
                criticRating = game.aggregatedRating.toInt(),
                developedBy = game.involvedCompaniesList.filter { it.developer }.map { it.company.name },
                publishedBy = game.involvedCompaniesList.filter { it.publisher }.map { it.company.name },
                genres = game.genresList.map { Mapper.genre(it) },
                themes = game.themesList.map { Mapper.theme(it) },
                screenshotUrls = listOf(),
                videoUrls = listOf(),
                features = listOf(),
                perspectives = listOf()
            )
        }
    }
}