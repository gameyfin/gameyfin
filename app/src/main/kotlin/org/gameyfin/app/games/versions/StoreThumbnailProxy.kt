package org.gameyfin.app.games.versions

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.TimeUnit

private val log = KotlinLogging.logger {}

/**
 * Some image hosts refuse requests from browsers (e.g. Epic's CDN answers 403 depending on the client), so thumbnails
 * of these hosts are served through Gameyfin. Only the listed hosts can be proxied.
 */
@Service
class StoreThumbnailProxy {

    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    fun isProxyable(url: String): Boolean = allowed(runCatching { URI(url) }.getOrNull())

    /** The URL the UI should use for [url]. Unchanged if the host does not need to be proxied. */
    fun rewrite(url: String?): String? =
        if (url != null && isProxyable(url)) "/images/store-thumbnail?url=" + URLEncoder.encode(url, StandardCharsets.UTF_8)
        else url

    fun fetch(url: String): Thumbnail? {
        val uri = runCatching { URI(url) }.getOrNull()
        if (!allowed(uri)) return null
        return try {
            val request = HttpRequest.newBuilder(uri!!).timeout(Duration.ofSeconds(10))
                .header("Accept", "image/jpeg,image/png,image/webp,image/avif,image/*;q=0.8")
                .header("User-Agent", "Gameyfin")
                .GET().build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
            val type = response.headers().firstValue("Content-Type").orElse("").substringBefore(';').trim().lowercase()
            if (response.statusCode() != 200 || type !in IMAGE_TYPES || response.body().size > MAX_BYTES) null
            else Thumbnail(response.body(), type)
        } catch (e: Exception) {
            log.debug(e) { "Could not load store thumbnail $url" }
            null
        }
    }

    private fun allowed(uri: URI?): Boolean {
        if (uri == null || uri.scheme != "https" || uri.userInfo != null || uri.port != -1) return false
        val host = uri.host?.lowercase() ?: return false
        return host == "epicgames.com" || host.endsWith(".epicgames.com")
    }

    class Thumbnail(val bytes: ByteArray, val contentType: String)

    companion object {
        private val IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/avif", "image/gif")
        private const val MAX_BYTES = 2 * 1024 * 1024
    }
}

@RestController
@RequestMapping("/images/store-thumbnail")
@RolesAllowed(Role.Names.ADMIN)
class StoreThumbnailController(private val proxy: StoreThumbnailProxy) {

    @GetMapping
    fun get(@RequestParam url: String): ResponseEntity<ByteArrayResource> {
        val thumbnail = proxy.fetch(url) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok()
            .header("Content-Type", thumbnail.contentType)
            .header("X-Content-Type-Options", "nosniff")
            .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate())
            .body(ByteArrayResource(thumbnail.bytes))
    }
}
