package org.gameyfin.plugins.metadata.steam

// Resilience4j
import io.github.resilience4j.bulkhead.Bulkhead
import io.github.resilience4j.bulkhead.BulkheadConfig
import io.github.resilience4j.decorators.Decorators
import io.github.resilience4j.ratelimiter.RateLimiter
import io.github.resilience4j.ratelimiter.RateLimiterConfig
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.gameyfin.pluginapi.core.wrapper.GameyfinPlugin
import org.gameyfin.pluginapi.gamemetadata.GameMetadata
import org.gameyfin.pluginapi.gamemetadata.GameMetadataProvider
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.gameyfin.plugins.metadata.steam.dto.SteamDetailsResultWrapper
import org.gameyfin.plugins.metadata.steam.dto.SteamGame
import org.gameyfin.plugins.metadata.steam.dto.SteamPlatforms
import org.gameyfin.plugins.metadata.steam.dto.SteamSearchResult
import org.gameyfin.plugins.metadata.steam.mapper.Mapper
import org.gameyfin.plugins.metadata.steam.util.SteamDateSerializer
import org.jsoup.Jsoup
import org.pf4j.Extension
import org.pf4j.PluginWrapper
import org.slf4j.LoggerFactory
import java.net.URI
import java.time.Duration
import java.time.Instant

class SteamPlugin(wrapper: PluginWrapper) : GameyfinPlugin(wrapper) {

    companion object {
        val json = Json {
            isLenient = true
            ignoreUnknownKeys = true
        }

        val dateSerializer = SteamDateSerializer()
    }

    @Suppress("Unused")
    @Extension(ordinal = 3)
    class SteamMetadataProvider : GameMetadataProvider {
        private val log = LoggerFactory.getLogger(javaClass)

        private val client = HttpClient(CIO) {
            // Use a fake browser user agent to avoid being blocked by Steam
            BrowserUserAgent()

            install(ContentNegotiation) {
                json(json)
            }
        }

        companion object {
            private val rateLimiter: RateLimiter = RateLimiter.of(
                "steam-api",
                RateLimiterConfig.custom()
                    .limitForPeriod(4)
                    .limitRefreshPeriod(Duration.ofSeconds(1))
                    .timeoutDuration(Duration.ofMinutes(10))
                    .build()
            )
            private val bulkhead: Bulkhead = Bulkhead.of(
                "steam-api",
                BulkheadConfig.custom()
                    .maxConcurrentCalls(8)
                    .maxWaitDuration(Duration.ofMinutes(10))
                    .build()
            )
        }

        // SteamVR support is not properly reflected in the store API, so we cannot reliably detect VR games
        override val supportedPlatforms: Set<Platform> =
            setOf(Platform.PC_MICROSOFT_WINDOWS, Platform.LINUX, Platform.MAC)

        /**
         * The Steam Store API I am using provides far less info than IGDB for example
         * See it more as a proof of concept than a fully functional plugin
         **/
        override fun fetchByTitle(
            gameTitle: String,
            platformFilter: Set<Platform>,
            maxResults: Int
        ): List<GameMetadata> {
            val searchResult: List<SteamGame> = try {
                steamApiCall { searchStore(gameTitle, platformFilter) }
            } catch (e: Exception) {
                log.error("Failed to search Steam store: ${e.message}")
                emptyList()
            }
            if (searchResult.isEmpty()) return emptyList()

            // Use fuzzy search to find the best matching game name
            val bestMatchingTitles = FuzzySearch.extractTop(gameTitle, searchResult.map { it.name }, maxResults)
            val bestMatchingTitleStrings = bestMatchingTitles.map { it.string }
            val bestMatchesMap = bestMatchingTitles.associateBy({ it.string }, { it.score })

            // Filter the games to only include those that match the best matching titles
            var bestMatches = searchResult.filter { it.name in bestMatchingTitleStrings }

            // If we have more than maxResults, sort by the best match score and take the top results
            bestMatches = bestMatches.filter { it.name in bestMatchesMap.keys }
                .sortedByDescending { bestMatchesMap[it.name] }

            return bestMatches.mapNotNull { steamGame ->
                try {
                    steamApiCall { getGameDetails(steamGame.id, platformFilter) }
                } catch (e: Exception) {
                    log.warn("Failed to fetch details for app ${steamGame.id}: ${e.message}")
                    null
                }
            }.take(maxResults)
        }

        // Helper to enforce rate limit + bulkhead around suspend HTTP operations
        private fun <T> steamApiCall(block: suspend () -> T): T {
            val supplier = { runBlocking { block() } }
            val decorated = Decorators.ofSupplier(supplier)
                .withBulkhead(bulkhead)
                .withRateLimiter(rateLimiter)
                .decorate()
            return decorated.get()
        }

        override fun fetchById(id: String): GameMetadata? {
            val intId = id.toIntOrNull() ?: return null
            return try {
                steamApiCall { getGameDetails(intId) }
            } catch (e: Exception) {
                log.warn("Failed to fetch details for app $intId: ${e.message}")
                null
            }
        }

        private suspend fun searchStore(title: String, platformFilter: Set<Platform>): List<SteamGame> {
            val response = client.get("https://store.steampowered.com/api/storesearch") {
                parameter("term", title)
                parameter("cc", "en")
                parameter("l", "en")
            }

            if (response.status == HttpStatusCode.Forbidden) {
                log.warn("Steam API rate limit hit; backing off and returning empty result")
                return emptyList()
            }

            if (response.status != HttpStatusCode.OK) {
                log.warn("Steam search returned HTTP ${response.status}")
                return emptyList()
            }

            val searchResult: SteamSearchResult = response.body()

            val filteredByPlatform = if (platformFilter.isNotEmpty()) {
                searchResult.items.filter { game ->
                    val platformsSupportedByGame = toGameyfinPlatforms(game.platforms)
                    platformFilter.any { it in platformsSupportedByGame }
                }
            } else {
                searchResult.items
            }

            return filteredByPlatform
        }

        private suspend fun getGameDetails(id: Int, platformFilter: Set<Platform> = emptySet()): GameMetadata? {
            val response = client.get("https://store.steampowered.com/api/appdetails") {
                parameter("appids", id)
                parameter("cc", "en")
                parameter("l", "en")
            }

            if (response.status == HttpStatusCode.Forbidden) {
                log.warn("Steam API rate limit hit; backing off and returning empty result")
                return null
            }

            if (response.status != HttpStatusCode.OK) return null

            val responseBody: String = response.bodyAsText(Charsets.UTF_8)
            val steamDetailsResultWrapper: Map<Int, SteamDetailsResultWrapper> = json.decodeFromString(responseBody)

            if (!steamDetailsResultWrapper.containsKey(id)) return null
            if (steamDetailsResultWrapper[id]?.success != true) return null

            val game = steamDetailsResultWrapper[id]?.data ?: return null

            if (game.type != "game") return null

            // The returned game should only contain the platforms we are interested in (if any filter is set)
            val gamePlatforms = if (platformFilter.isNotEmpty()) {
                toGameyfinPlatforms(game.platforms).intersect(platformFilter)
            } else {
                toGameyfinPlatforms(game.platforms)
            }

            // If the game does not support any of the requested platforms, skip it
            if (gamePlatforms.isEmpty()) return null

            // This is as much as I can get from the Steam Store API
            val metadata = GameMetadata(
                originalId = id.toString(),
                title = sanitizeTitle(game.name),
                platforms = gamePlatforms,
                description = game.shortDescription, // Using short description since the detailed description often contains just some ads for the Battle Pass etc.
                coverUrls = game.headerImage?.let { URI(it) }?.let { listOf(it) }?.toSet(),
                release = parseOriginalReleaseDateFromStorePage(id) ?: game.releaseDate?.date,
                developedBy = game.developers?.toSet(),
                publishedBy = game.publishers?.toSet(),
                genres = game.genres?.let { genre -> genre.map { Mapper.genre(it) }.toSet() },
                keywords = game.categories?.mapNotNull { it.description }?.toSet(),
                screenshotUrls = game.screenshots?.map { URI(it.pathFull) }?.toSet(),
                videoUrls = game.movies?.mapNotNull { video -> video.webm?.let { URI(it.max) } }?.toSet()
            )

            return metadata
        }

        /**
         * The API only provides the release date on Steam, not the original release date.
         * However, it is possible to get the original release date from the Steam store page.
         */
        private suspend fun parseOriginalReleaseDateFromStorePage(appId: Int): Instant? {
            val response = client.get("https://store.steampowered.com/app/$appId") {
                // Set language to English to avoid issues with different languages
                cookie("Steam_Language", "english")
                // Skip Steam age check
                cookie("birthtime", "-2208989360")
                cookie("lastagecheckage", "1-January-1900")
            }

            if (response.status == HttpStatusCode.Forbidden) {
                log.warn("Steam web page responded 403 Forbidden for app $appId; can't parse original release date")
                return null
            }

            if (response.status != HttpStatusCode.OK) return null

            val html: String = response.bodyAsText(Charsets.UTF_8)
            val document = Jsoup.parse(html)
            val releaseDateText = document.selectFirst("div.release_date div.date") ?: return null

            return dateSerializer.deserialize(releaseDateText.text())
        }

        /**
         * Often titles on Steam contain copyright symbols which makes matching between different providers harder
         * This method removes those symbols
         */
        private fun sanitizeTitle(originalTitle: String): String {
            val unwantedChars = setOf('™', '©', '®')
            return originalTitle.filter { it !in unwantedChars }.trim()
        }

        /**
         * Determine supported Gameyfin platforms for a Steam game based on its platform flags
         */
        private fun toGameyfinPlatforms(steamPlatforms: SteamPlatforms): Set<Platform> {
            val gameyfinPlatforms = mutableSetOf<Platform>()
            if (steamPlatforms.windows) gameyfinPlatforms.add(Platform.PC_MICROSOFT_WINDOWS)
            if (steamPlatforms.linux) gameyfinPlatforms.add(Platform.LINUX)
            if (steamPlatforms.mac) gameyfinPlatforms.add(Platform.MAC)
            return gameyfinPlatforms
        }
    }
}

