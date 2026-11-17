package org.gameyfin.plugins.store.steam

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Fetches JSON documents for the Steam store provider. Injectable so that tests run offline. */
fun interface SteamStoreHttp {
    fun getJson(url: String): JsonElement
}

/** Uses one shared [HttpClient], released by [shutdown], and spaces requests out to stay polite to Steam. */
internal object JdkSteamStoreHttp : SteamStoreHttp {
    private const val MIN_INTERVAL_MS = 250L

    @Volatile
    private var client: HttpClient? = null
    private val throttleLock = Any()
    private var lastRequestAt = 0L

    private fun client(): HttpClient = client ?: synchronized(this) {
        client ?: HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
            .also { client = it }
    }

    private fun throttle() = synchronized(throttleLock) {
        val wait = lastRequestAt + MIN_INTERVAL_MS - System.currentTimeMillis()
        if (wait > 0) Thread.sleep(wait)
        lastRequestAt = System.currentTimeMillis()
    }

    override fun getJson(url: String): JsonElement {
        throttle()
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .header("User-Agent", "Gameyfin")
            .GET()
            .build()
        val response = client().send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) { "Steam returned HTTP ${response.statusCode()} for $url" }
        check(response.body().isNotBlank()) { "Steam returned an empty response for $url" }
        return Json.parseToJsonElement(response.body())
    }

    fun shutdown() {
        synchronized(this) {
            client?.close()
            client = null
        }
    }
}
