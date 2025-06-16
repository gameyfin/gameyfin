package org.gameyfin.plugins.metadata.steam

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
import org.gameyfin.plugins.metadata.steam.dto.SteamDetailsResultWrapper
import org.gameyfin.plugins.metadata.steam.dto.SteamGame
import org.gameyfin.plugins.metadata.steam.dto.SteamSearchResult
import org.gameyfin.plugins.metadata.steam.mapper.Mapper
import org.pf4j.Extension
import org.pf4j.PluginWrapper
import org.slf4j.LoggerFactory
import java.net.URI

class SteamPlugin(wrapper: PluginWrapper) : GameyfinPlugin(wrapper) {

    companion object {
        val json = Json {
            isLenient = true
            ignoreUnknownKeys = true
        }
    }

    @Extension(ordinal = 3)
    class SteamMetadataProvider : GameMetadataProvider {
        private val log = LoggerFactory.getLogger(javaClass)

        val client = HttpClient(CIO) {
            // Use a fake browser user agent to avoid being blocked by Steam
            BrowserUserAgent()

            install(ContentNegotiation) {
                json(json)
            }
        }

        /**
         * The Steam Store API I am using provides far less info than IGDB for example
         * See it more as a proof of concept than a fully functional plugin
         **/
        override fun fetchByTitle(gameTitle: String, maxResults: Int): List<GameMetadata> {
            val searchResult: List<SteamGame> = runBlocking { searchStore(gameTitle) }
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

            return runBlocking { bestMatches.map { getGameDetails(it.id) } }
                .filterNotNull()
                .take(maxResults)
        }

        override fun fetchById(id: String): GameMetadata? {
            val id = id.toIntOrNull() ?: return null
            return runBlocking { getGameDetails(id) }
        }

        private suspend fun searchStore(title: String): List<SteamGame> {
            return try {
                val response = client.get("https://store.steampowered.com/api/storesearch") {
                    parameter("term", title)
                    parameter("cc", "en")
                    parameter("l", "en")
                }
                val searchResult: SteamSearchResult = response.body()
                searchResult.items
            } catch (e: Exception) {
                log.error("Failed to search Steam store: ${e.message}")
                emptyList()
            }
        }

        private suspend fun getGameDetails(id: Int): GameMetadata? {
            val response = client.get("https://store.steampowered.com/api/appdetails") {
                parameter("appids", id)
                parameter("cc", "en")
                parameter("l", "en")
            }

            if (response.status != HttpStatusCode.OK) return null

            val responseBody: String = response.bodyAsText(Charsets.UTF_8)
            val steamDetailsResultWrapper: Map<Int, SteamDetailsResultWrapper> = json.decodeFromString(responseBody)

            if (!steamDetailsResultWrapper.containsKey(id)) return null
            if (steamDetailsResultWrapper[id]?.success != true) return null

            val game = steamDetailsResultWrapper[id]?.data ?: return null

            if (game.type != "game") return null

            // This is as much as I can get from the Steam Store API
            val metadata = GameMetadata(
                originalId = id.toString(),
                title = sanitizeTitle(game.name),
                description = game.detailedDescription,
                coverUrls = game.headerImage?.let { URI(it) }?.let { listOf(it) },
                release = game.releaseDate?.date,
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
         * Often titles on Steam contain copyright symbols which makes matching between different providers harder
         * This method removes those symbols
         */
        private fun sanitizeTitle(originalTitle: String): String {
            val unwantedChars = setOf('™', '©', '®')
            return originalTitle.filter { it !in unwantedChars }.trim()
        }
    }
}