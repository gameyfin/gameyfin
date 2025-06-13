package de.grimsi.gameyfinplugins.steamgriddb.api

import de.grimsi.gameyfinplugins.steamgriddb.dto.SteamGridDbGameResult
import de.grimsi.gameyfinplugins.steamgriddb.dto.SteamGridDbGridResult
import de.grimsi.gameyfinplugins.steamgriddb.dto.SteamGridDbSearchResult
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json


class SteamGridDbApiClient(private val apiKey: String) {
    companion object {
        private val json = Json {
            isLenient = true
            ignoreUnknownKeys = true
        }
        private const val BASE_URL = "https://www.steamgriddb.com/api/v2"
        private const val COVER_SIZES = "600x900,342x482,660x930"
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    suspend fun isApiKeyValid(): Boolean {
        return try {
            val response = get("grids/game/1")
            response.status == HttpStatusCode.OK
        } catch (_: Exception) {
            false
        }
    }

    suspend fun search(term: String, block: HttpRequestBuilder.() -> Unit = {}): SteamGridDbSearchResult {
        return get("search/autocomplete/${term.encodeURLPath(encodeSlash = true, encodeEncoded = false)}", block).body()
    }

    suspend fun grids(gameId: Int, block: HttpRequestBuilder.() -> Unit = {}): SteamGridDbGridResult {
        return get("grids/game/$gameId") {
            url {
                parameters.append("dimensions", COVER_SIZES)
            }
            block()
        }.body()
    }

    suspend fun game(gameId: Int, block: HttpRequestBuilder.() -> Unit = {}): SteamGridDbGameResult {
        return get("games/id/$gameId", block).body()
    }

    private suspend fun get(endpoint: String, block: HttpRequestBuilder.() -> Unit = {}): HttpResponse {
        return client.get("$BASE_URL/$endpoint".encodeURLPath(encodeEncoded = false)) {
            bearerAuth(apiKey)
            block()
        }
    }
}