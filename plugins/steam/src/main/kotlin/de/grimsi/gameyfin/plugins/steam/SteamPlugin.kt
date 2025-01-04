package de.grimsi.gameyfin.plugins.steam

import de.grimsi.gameyfin.pluginapi.core.GameyfinPlugin
import de.grimsi.gameyfin.pluginapi.core.PluginConfigElement
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadata
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadataProvider
import de.grimsi.gameyfin.plugins.steam.dto.SteamDetailsResultWrapper
import de.grimsi.gameyfin.plugins.steam.dto.SteamGame
import de.grimsi.gameyfin.plugins.steam.dto.SteamSearchResult
import de.grimsi.gameyfin.plugins.steam.mapper.toGenre
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.pf4j.Extension
import org.pf4j.PluginWrapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

class SteamPlugin(wrapper: PluginWrapper) : GameyfinPlugin(wrapper) {
    override val configMetadata: List<PluginConfigElement> = emptyList()

    override fun validateConfig(config: Map<String, String?>): Boolean {
        return true
    }

    @Extension
    class SteamMetadataProvider : GameMetadataProvider {
        val log: Logger = LoggerFactory.getLogger(javaClass)

        val client = HttpClient(CIO) {
            install(ContentNegotiation) {
                json()
            }
        }

        override fun fetchMetadata(gameId: String): GameMetadata? {
            val searchResult: List<SteamGame> = runBlocking { searchStore(gameId) }
            if (searchResult.isEmpty()) return null

            val bestMatchingTitle = FuzzySearch.extractOne(gameId, searchResult.map { it.name }).string
            val bestMatch = searchResult.find { it.name == bestMatchingTitle } ?: return null

            return runBlocking { getGameDetails(bestMatch.id) }
        }

        private suspend fun searchStore(title: String): List<SteamGame> {
            val encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
            return try {
                val response = client.get("https://store.steampowered.com/api/storesearch") {
                    parameter("term", encodedTitle)
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
            val steamDetailsResultWrapper: Map<Int, SteamDetailsResultWrapper> = Json.decodeFromString(responseBody)

            if (!steamDetailsResultWrapper.containsKey(id)) return null

            val game = steamDetailsResultWrapper[id]?.data ?: return null

            val metadata = GameMetadata(
                title = game.name,
                description = game.detailedDescription,
                coverUrl = URI(game.headerImage),
                release = game.releaseDate?.date,
                developedBy = game.developers.toSet(),
                publishedBy = game.publishers.toSet(),
                genres = game.genres.map { toGenre(it) }.toSet(),
                keywords = game.categories.mapNotNull { it.description }.toSet(),
                screenshotUrls = game.screenshots.map { URI(it.pathFull!!) }.toSet(),
                videoUrls = game.movies.map { URI(it.webm?.max!!) }.toSet()
            )

            return metadata
        }

        private fun string(json: JsonObject, key: String): String {
            return json[key]?.jsonPrimitive?.content ?: ""
        }

        private fun stringList(json: JsonObject, key: String): List<String> {
            return json[key]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        }

        fun date(dateString: String): Instant {
            val formatter = DateTimeFormatter.ofPattern("dd MMM, yyyy", Locale.ENGLISH)
            val localDate = LocalDate.parse(dateString, formatter)
            return localDate.atStartOfDay().toInstant(ZoneOffset.UTC)
        }
    }
}