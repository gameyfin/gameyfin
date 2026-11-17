package org.gameyfin.plugins.store.epic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Minimal JSON-over-HTTP abstraction so that the store logic can be tested without network access. */
interface EpicHttp : AutoCloseable {
    fun getJson(uri: URI): JsonElement

    fun postJson(uri: URI, body: String): JsonElement
}

class JavaEpicHttp : EpicHttp {
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    override fun getJson(uri: URI): JsonElement = send(
        HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .header("User-Agent", "Gameyfin")
            .GET()
            .build()
    )

    override fun postJson(uri: URI, body: String): JsonElement = send(
        HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("User-Agent", "Gameyfin")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
    )

    private fun send(request: HttpRequest): JsonElement {
        val uri = request.uri()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) {
            "Version source returned HTTP ${response.statusCode()} for $uri"
        }
        check(response.body().isNotBlank()) { "Version source returned an empty response for $uri" }
        return try {
            Json.parseToJsonElement(response.body())
        } catch (e: Exception) {
            throw IllegalStateException("Version source returned invalid JSON for $uri", e)
        }
    }

    override fun close() = client.close()
}
