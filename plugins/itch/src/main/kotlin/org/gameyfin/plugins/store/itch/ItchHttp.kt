package org.gameyfin.plugins.store.itch

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Fetches public itch.io documents as text. Injectable so that tests run offline. */
fun interface ItchHttp {
    fun getText(url: String): String
}

/** Uses one shared [HttpClient], released by [shutdown] when the plugin stops. */
internal object JdkItchHttp : ItchHttp {
    private const val MAX_BYTES = 5 * 1024 * 1024

    @Volatile
    private var client: HttpClient? = null

    private fun client(): HttpClient = client ?: synchronized(this) {
        client ?: HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
            .also { client = it }
    }

    override fun getText(url: String): String {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "text/html,application/json")
            .header("User-Agent", "Gameyfin")
            .GET()
            .build()
        val response = client().send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() in 200..299) { "itch.io returned HTTP ${response.statusCode()} for $url" }
        // A redirect must not lead somewhere else than itch.io, e.g. to a custom domain of a developer
        check(isItchHost(response.uri().host)) { "itch.io redirected to ${response.uri().host}" }
        check(response.body().length <= MAX_BYTES) { "itch.io returned an unexpectedly large response for $url" }
        return response.body()
    }

    fun shutdown() {
        synchronized(this) {
            client?.close()
            client = null
        }
    }
}

internal fun isItchHost(host: String?): Boolean {
    val h = host?.lowercase() ?: return false
    return h == "itch.io" || h.endsWith(".itch.io")
}
