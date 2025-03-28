package de.grimsi.gameyfin.plugins.igdb

import com.api.igdb.apicalypse.APICalypse
import com.api.igdb.request.IGDBWrapper
import com.api.igdb.request.TwitchAuthenticator
import com.api.igdb.request.games
import de.grimsi.gameyfin.pluginapi.core.GameyfinPlugin
import de.grimsi.gameyfin.pluginapi.core.PluginConfigElement
import de.grimsi.gameyfin.pluginapi.core.PluginConfigError
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadata
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadataProvider
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.pf4j.Extension
import org.pf4j.PluginWrapper
import java.time.Instant

class IgdbPlugin(wrapper: PluginWrapper) : GameyfinPlugin(wrapper) {

    override val configMetadata: List<PluginConfigElement> = listOf(
        PluginConfigElement("clientId", "Twitch client ID", "Your Twitch Client ID"),
        PluginConfigElement("clientSecret", "Twitch client secret", "Your Twitch Client Secret", true)
    )

    override fun validateConfig(config: Map<String, String?>): Boolean {
        try {
            authenticate()
            return true
        } catch (e: PluginConfigError) {
            log.error(e.message)
            return false
        }
    }

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
    class IgdbMetadataProvider : GameMetadataProvider {

        private val QUERY_FIELDS = listOf(
            "slug",
            "name",
            "summary",
            "first_release_date",
            "rating",
            "aggregated_rating",
            "total_rating",
            "category",
            "multiplayer_modes.lancoop",
            "game_modes.slug",
            "game_modes.name",
            "cover.image_id",
            "screenshots.image_id",
            "videos.video_id",
            "involved_companies.company.slug",
            "involved_companies.company.name",
            "involved_companies.developer",
            "involved_companies.publisher",
            "involved_companies.company.logo.image_id",
            "genres.slug",
            "genres.name",
            "keywords.slug",
            "keywords.name",
            "themes.slug",
            "themes.name",
            "player_perspectives.slug",
            "player_perspectives.name",
            "platforms.slug",
            "platforms.name",
            "platforms.platform_logo.image_id"
        ).joinToString(",")

        override fun fetchMetadata(gameId: String): GameMetadata? {
            val findBySlugQuery = APICalypse()
                .fields(QUERY_FIELDS)
                .where("slug = \"${guessSlug(gameId)}\"")

            // First step: Try to find the game by guessing the slug
            var game = IGDBWrapper.games(findBySlugQuery).firstOrNull()

            // Second step: Try a fuzzy search
            if (game == null) {
                val searchByNameQuery = APICalypse()
                    .fields(QUERY_FIELDS)
                    .limit(100)
                    .search(gameId)

                // Use IGDBs search function to get a list of games that match the search query
                val games = IGDBWrapper.games(searchByNameQuery)

                if (games.isEmpty()) return null

                // Use fuzzy search to find the best matching game name
                val bestMatchingName = FuzzySearch.extractOne(gameId, games.map { it.name }).string

                game = games.find { it.name == bestMatchingName } ?: return null
            }

            return GameMetadata(
                title = game.name,
                description = game.summary,
                coverUrl = Mapper.cover(game.cover),
                release = Instant.ofEpochSecond(game.firstReleaseDate.seconds),
                userRating = game.rating.toInt(),
                criticRating = game.aggregatedRating.toInt(),
                developedBy = game.involvedCompaniesList.filter { it.developer }.map { it.company.name }.toSet(),
                publishedBy = game.involvedCompaniesList.filter { it.publisher }.map { it.company.name }.toSet(),
                genres = game.genresList.map { Mapper.genre(it) }.toSet(),
                themes = game.themesList.map { Mapper.theme(it) }.toSet(),
                keywords = game.keywordsList.map { it.name }.toSet(),
                screenshotUrls = game.screenshotsList.map { Mapper.screenshot(it) }.toSet(),
                videoUrls = game.videosList.map { Mapper.video(it) }.toSet(),
                features = Mapper.gameFeatures(game),
                perspectives = game.playerPerspectivesList.map { Mapper.playerPerspective(it) }.toSet()
            )
        }

        private fun guessSlug(gameId: String): String {
            return gameId.replace(" ", "-").lowercase()
        }
    }
}