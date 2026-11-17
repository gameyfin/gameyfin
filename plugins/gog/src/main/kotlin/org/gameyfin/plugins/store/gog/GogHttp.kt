package org.gameyfin.plugins.store.gog

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Minimal HTTP layer so that the provider can be tested offline. */
interface GogHttp {
    fun getJson(uri: URI): JsonElement
}

/** Uses one shared [HttpClient], which is released by [shutdown] when the plugin stops. */
object JdkGogHttp : GogHttp {
    @Volatile
    private var client: HttpClient? = null

    private fun client(): HttpClient = client ?: synchronized(this) {
        client ?: HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
            .also { client = it }
    }

    override fun getJson(uri: URI): JsonElement {
        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .header("User-Agent", "Gameyfin")
            .GET()
            .build()
        val response = client().send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) { "Version source returned HTTP ${response.statusCode()} for $uri" }
        check(response.body().isNotBlank()) { "Version source returned an empty response for $uri" }
        return Json.parseToJsonElement(response.body())
    }

    fun shutdown() {
        synchronized(this) {
            client?.close()
            client = null
        }
    }
}
