package de.grimsi.gameyfin.plugins.igdb

import com.api.igdb.apicalypse.APICalypse
import com.api.igdb.exceptions.RequestException
import com.api.igdb.request.IGDBWrapper
import com.api.igdb.request.TwitchAuthenticator
import com.api.igdb.request.games
import de.grimsi.gameyfin.pluginapi.core.config.*
import de.grimsi.gameyfin.pluginapi.core.wrapper.ConfigurableGameyfinPlugin
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadata
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadataProvider
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.pf4j.Extension
import org.pf4j.PluginWrapper
import org.slf4j.LoggerFactory
import proto.Game
import java.time.Instant
import java.util.concurrent.TimeUnit

class IgdbPlugin(wrapper: PluginWrapper) : ConfigurableGameyfinPlugin(wrapper) {

    override val configMetadata: PluginConfigMetadata = listOf(
        ConfigMetadata(
            key = "clientId",
            type = String::class.java,
            label = "Twitch client ID",
            description = "Your Twitch Client ID"
        ),
        ConfigMetadata(
            key = "clientSecret",
            type = String::class.java,
            label = "Twitch client secret",
            description = "Your Twitch Client Secret",
            isSecret = true
        )
    )

    override fun validateConfig(config: Map<String, String?>): PluginConfigValidationResult {
        val pluginConfigValidationResult = super.validateConfig(config)

        if (pluginConfigValidationResult.result == PluginConfigValidationResultType.INVALID) {
            return pluginConfigValidationResult
        }

        try {
            val clientIdToValidate = config["clientId"]
            val clientSecretToValidate = config["clientSecret"]
            authenticate(clientIdToValidate, clientSecretToValidate)
            return PluginConfigValidationResult.VALID
        } catch (e: PluginConfigError) {
            log.error(e.message)
            return PluginConfigValidationResult.INVALID(
                mapOf(
                    "clientId" to "Invalid client ID and/or client secret",
                    "clientSecret" to "Invalid client ID and/or client secret"
                )
            )
        }
    }

    override fun start() {
        try {
            authenticate(config("clientId"), config("clientSecret"))
        } catch (e: PluginConfigError) {
            log.error(e.message)
        }
    }

    override fun stop() {
        log.debug("IgdbPlugin.stop()")
    }

    private fun authenticate(clientId: String? = null, clientSecret: String? = null) {
        log.debug("Authenticating on Twitch API...")

        val clientId: String = clientId ?: throw PluginConfigError("Twitch Client ID not set")
        val clientSecret: String = clientSecret ?: throw PluginConfigError("Twitch Client Secret not set")

        val token = TwitchAuthenticator.requestTwitchToken(clientId, clientSecret)
            ?: throw PluginConfigError("Failed to authenticate on Twitch API with provided credentials")

        IGDBWrapper.setCredentials(clientId, token.access_token)

        log.debug("Authentication successful")
    }

    @Extension
    class IgdbMetadataProvider : GameMetadataProvider {

        companion object {
            private val log = LoggerFactory.getLogger(this::class.java)

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
                "videos.name",
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
        }

        override fun fetchMetadata(gameId: String, maxResults: Int): List<GameMetadata> {
            try {
                // Note: Limit is intentionally set high because IGDBs ranking algorithm is not very good
                val searchByNameQuery = APICalypse()
                    .fields(QUERY_FIELDS)
                    .limit(100)
                    .search(gameId)

                // Use IGDBs search function to get a list of games that match the search query
                var games = IGDBWrapper.games(searchByNameQuery)

                if (games.isEmpty()) return emptyList()

                // Use fuzzy search to find the best matching game name
                val bestMatchingTitles = FuzzySearch.extractTop(gameId, games.map { it.name }, maxResults)
                games = bestMatchingTitles.mapNotNull { title -> games.find { it.name == title.string } }

                return games.map { toGameMetadata(it) }
            } catch (e: RequestException) {
                // FIXME: Handle rate limit errors with exponential backoff
                if (e.statusCode == 429) {
                    val randomInterval = (1..5).random().toLong()
                    log.warn("IGDB rate limit exceeded, retrying in $randomInterval seconds...")
                    TimeUnit.SECONDS.sleep(randomInterval)
                    return fetchMetadata(gameId, maxResults)
                }

                log.error("Request to IGDB API failed with HTTP ${e.statusCode}")
            }

            return emptyList()
        }

        private fun toGameMetadata(game: Game): GameMetadata {
            return GameMetadata(
                originalId = game.slug,
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
                videoUrls = game.videosList
                    // Lots of gameplay videos hosted on YouTube are blocked from viewing on external sites due to age ratings
                    // Trailers usually are not affected so we filter for them
                    // see https://support.google.com/youtube/answer/2802167
                    .filter { it.name.equals("trailer", ignoreCase = true) }
                    .map { Mapper.video(it) }.toSet(),
                features = Mapper.gameFeatures(game),
                perspectives = game.playerPerspectivesList.map { Mapper.playerPerspective(it) }.toSet()
            )
        }
    }
}