package org.gameyfin.plugins.metadata.igdb

import com.api.igdb.apicalypse.APICalypse
import com.api.igdb.request.IGDBWrapper
import com.api.igdb.request.TwitchAuthenticator
import com.api.igdb.request.games
import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.bulkhead.BulkheadConfig
import io.github.resilience4j.decorators.Decorators
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.gameyfin.pluginapi.core.config.ConfigMetadata
import org.gameyfin.pluginapi.core.config.PluginConfigError
import org.gameyfin.pluginapi.core.config.PluginConfigMetadata
import org.gameyfin.pluginapi.core.config.PluginConfigValidationResult
import org.gameyfin.pluginapi.core.wrapper.ConfigurableGameyfinPlugin
import org.gameyfin.pluginapi.gamemetadata.GameMetadata
import org.gameyfin.pluginapi.gamemetadata.GameMetadataProvider
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.gameyfin.plugins.metadata.igdb.mapper.*
import org.pf4j.Extension
import org.pf4j.PluginWrapper
import proto.Game
import java.time.Duration
import java.time.Instant

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

        if (!pluginConfigValidationResult.isValid()) return pluginConfigValidationResult

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

    @Suppress("Unused")
    @Extension(ordinal = 2)
    class IgdbMetadataProvider : GameMetadataProvider {

        companion object {
            private val rateLimiter: RateLimiter = RateLimiter.of(
                "igdb-api",
                RateLimiterConfig.custom()
                    .limitForPeriod(4)
                    .limitRefreshPeriod(Duration.ofSeconds(1))
                    .timeoutDuration(Duration.ofMinutes(10))
                    .build()
            )
            private val bulkhead: Bulkhead = Bulkhead.of(
                "igdb-api",
                BulkheadConfig.custom()
                    .maxConcurrentCalls(8)
                    .maxWaitDuration(Duration.ofMinutes(10)) // Wait up to 10s for a slot
                    .build()
            )

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
                "artworks.image_id",
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

        override val supportedPlatforms: Set<Platform>
            get() = Platform.entries.toSet()

        override fun fetchByTitle(
            gameTitle: String,
            platformFilter: Set<Platform>,
            maxResults: Int
        ): List<GameMetadata> {
            val searchByNameQuery = APICalypse()
                .fields(QUERY_FIELDS)
                // Note: Limit is intentionally set high because IGDBs ranking algorithm is not very good
                .limit(100)
                .search(gameTitle)

            if (platformFilter.isNotEmpty()) {
                val platformFilterQuery = PlatformMapper.toIgdb(platformFilter)
                    .joinToString(separator = "\", \"", prefix = "platforms.slug = (\"", postfix = "\")")
                searchByNameQuery.where(platformFilterQuery)
            }

            // Use IGDBs search function to get a list of games that match the search query
            var games = queryIgdbGames(searchByNameQuery)

            if (games.isEmpty()) return emptyList()

            // Use fuzzy search to find the best matching game name
            val bestMatchingTitles = FuzzySearch.extractTop(gameTitle, games.map { it.name }, maxResults)
            val bestMatchingTitleStrings = bestMatchingTitles.map { it.string }
            val bestMatchesMap = bestMatchingTitles.associateBy({ it.string }, { it.score })

            // Filter the games to only include those that match the best matching titles
            games = games.filter { it.name in bestMatchingTitleStrings }

            // If we have more than maxResults, sort by the best match score and take the top results
            games = games.filter { it.name in bestMatchesMap.keys }
                .sortedByDescending { bestMatchesMap[it.name] }
                .take(maxResults)

            return games.map { toGameMetadata(it, platformFilter) }
        }

        override fun fetchById(id: String): GameMetadata? {
            // For slug we can limit the results to 1, since slugs are unique
            val findBySlugQuery = APICalypse()
                .fields(QUERY_FIELDS)
                .limit(1)
                .where("slug = \"$id\"")

            val game = queryIgdbGames(findBySlugQuery).firstOrNull()
            return game?.let { toGameMetadata(it, null) }
        }

        private fun queryIgdbGames(query: APICalypse): List<Game> {
            val supplier = { IGDBWrapper.games(query) }
            val decorated = Decorators.ofSupplier(supplier)
                .withBulkhead(bulkhead)
                .withRateLimiter(rateLimiter)
                .decorate()
            return decorated.get()
        }

        private fun toGameMetadata(game: Game, platformFilter: Set<Platform>?): GameMetadata {
            val supportedPlatforms = game.platformsList.map { PlatformMapper.toGameyfin(it.slug) }
            val filteredPlatforms = platformFilter?.let { filter -> supportedPlatforms.filter { it in filter } }
                ?: supportedPlatforms

            return GameMetadata(
                originalId = game.slug,
                title = game.name,
                platforms = filteredPlatforms.toSet(),
                description = game.summary,
                coverUrls = MediaMapper.cover(game.cover)?.let { listOf(it) }?.toSet(),
                headerUrls = game.artworksList.map { MediaMapper.header(it) }.toSet(),
                release = if (game.firstReleaseDate.seconds > 0) Instant.ofEpochSecond(game.firstReleaseDate.seconds) else null,
                userRating = game.rating.toInt(),
                criticRating = game.aggregatedRating.toInt(),
                developedBy = game.involvedCompaniesList.filter { it.developer }.map { it.company.name }.toSet(),
                publishedBy = game.involvedCompaniesList.filter { it.publisher }.map { it.company.name }.toSet(),
                genres = game.genresList.map { GenreMapper.genre(it) }.toSet(),
                themes = game.themesList.map { ThemeMapper.theme(it) }.toSet(),
                keywords = game.keywordsList.map { it.name }.toSet(),
                screenshotUrls = game.screenshotsList.map { MediaMapper.screenshot(it) }.toSet(),
                videoUrls = game.videosList
                    // Lots of gameplay videos hosted on YouTube are blocked from viewing on external sites due to age ratings
                    // Trailers usually are not affected so we filter for them
                    // see https://support.google.com/youtube/answer/2802167
                    .filter { it.name.equals("trailer", ignoreCase = true) }
                    .map { MediaMapper.video(it) }.toSet(),
                features = GameFeatureMapper.gameFeatures(game),
                perspectives = game.playerPerspectivesList.map { PlayerPerspectiveMapper.playerPerspective(it) }.toSet()
            )
        }
    }
}