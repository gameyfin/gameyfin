package de.grimsi.gameyfin.plugins.steam

import de.grimsi.gameyfin.pluginapi.core.GameyfinPlugin
import de.grimsi.gameyfin.pluginapi.core.PluginConfigElement
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadata
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadataProvider
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
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

@Serializable
data class SteamSearchResult(
    val total: Int,
    val items: List<SteamGame>
)

@Serializable
data class SteamGame(
    val type: String,
    val name: String,
    val id: Int,
    val price: Price?,
    val tiny_image: String,
    val metascore: String?,
    val platforms: Platforms,
    val streamingvideo: Boolean,
    val controller_support: String
)

@Serializable
data class Price(
    val currency: String,
    val initial: Int,
    val final: Int
)

@Serializable
data class Platforms(
    val windows: Boolean,
    val mac: Boolean,
    val linux: Boolean
)

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
            val url = "https://store.steampowered.com/api/storesearch?term=$encodedTitle&cc=en"
            return try {
                val searchResult: SteamSearchResult = client.get(url).body()
                searchResult.items
            } catch (e: Exception) {
                log.error("Failed to search Steam store: ${e.message}")
                emptyList()
            }
        }

        private suspend fun getGameDetails(id: Int): GameMetadata? {
            val url = "https://store.steampowered.com/api/appdetails?appids=$id"
            val response: JsonObject = (client.get(url).body() as JsonObject)[id.toString()]?.jsonObject ?: return null

            if (response["success"]?.jsonPrimitive?.boolean == false) return null

            val game = response["data"]?.jsonObject ?: return null

            val metadata = GameMetadata(
                title = string(game, "name"),
                description = string(game, "detailed_description"),
                coverUrl = URI("").toURL(),
                release = date(game["release_date"]?.jsonObject["date"]?.jsonPrimitive?.content!!),
                userRating = 0,
                criticRating = 0,
                developedBy = stringList(game, "developers").toSet(),
                publishedBy = stringList(game, "publishers").toSet(),
                genres = emptySet(),
                themes = emptySet(),
                keywords = emptySet(),
                screenshotUrls = emptySet(),
                videoUrls = emptySet(),
                features = emptySet(),
                perspectives = emptySet()
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