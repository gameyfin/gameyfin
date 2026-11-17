package org.gameyfin.plugins.store.steamcmd

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Fetches JSON documents from the steamcmd.net API. Injectable so that tests run offline. */
fun interface SteamCmdHttp {
    fun getJson(url: String): JsonElement
}

/** Uses one shared [HttpClient], released by [shutdown] when the plugin stops. */
internal object JdkSteamCmdHttp : SteamCmdHttp {
    @Volatile
    private var client: HttpClient? = null

    private fun client(): HttpClient = client ?: synchronized(this) {
        client ?: HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
            .also { client = it }
    }

    override fun getJson(url: String): JsonElement {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .header("User-Agent", "Gameyfin")
            .GET()
            .build()
        val response = client().send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) { "steamcmd.net returned HTTP ${response.statusCode()} for $url" }
        check(response.body().isNotBlank()) { "steamcmd.net returned an empty response for $url" }
        return Json.parseToJsonElement(response.body())
    }

    fun shutdown() {
        synchronized(this) {
            client?.close()
            client = null
        }
    }
}
