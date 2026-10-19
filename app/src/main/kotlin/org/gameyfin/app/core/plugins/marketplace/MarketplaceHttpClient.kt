package org.gameyfin.app.core.plugins.marketplace

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.Path
import kotlin.io.path.fileSize
import kotlin.io.path.outputStream

/**
 * HTTP client used for all marketplace related network traffic (index files, plugin downloads, checksums, logos).
 * Enforces timeouts, size limits and allowed URL schemes.
 */
@Component
class MarketplaceHttpClient(
    @Value($$"${gameyfin.plugins.marketplace.connect-timeout:10s}")
    connectTimeout: Duration = Duration.ofSeconds(10),
    @Value($$"${gameyfin.plugins.marketplace.request-timeout:2m}")
    private val requestTimeout: Duration = Duration.ofMinutes(2),
    @Value($$"${gameyfin.plugins.marketplace.allow-insecure-urls:false}")
    val allowInsecureUrls: Boolean = false,
    @Value($$"${spring.application.version:unknown}")
    applicationVersion: String = "unknown"
) {
    companion object {
        private val log = KotlinLogging.logger {}

        const val MAX_INDEX_SIZE: Long = 5L * 1024 * 1024
        const val MAX_LOGO_SIZE: Long = 1L * 1024 * 1024
        const val MAX_CHECKSUM_SIZE: Long = 16L * 1024
        const val MAX_PLUGIN_SIZE: Long = 512L * 1024 * 1024
    }

    private val userAgent = "Gameyfin/$applicationVersion (+https://github.com/gameyfin/gameyfin)"

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(connectTimeout)
        // NORMAL does not follow redirects from HTTPS to HTTP
        .followRedirects(HttpClient.Redirect.NORMAL)
        .proxy(ProxySelector.getDefault())
        .build()

    /**
     * @throws IllegalArgumentException if the URL scheme is not allowed
     */
    fun checkAllowed(uri: URI) {
        val scheme = uri.scheme?.lowercase()
        val allowed = if (allowInsecureUrls) setOf("https", "http", "file") else setOf("https")
        require(scheme in allowed) { "URL scheme '${uri.scheme}' is not allowed (URL: $uri)" }
    }

    fun getBytes(uri: URI, maxBytes: Long): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        transfer(uri, buffer, maxBytes)
        return buffer.toByteArray()
    }

    fun getString(uri: URI, maxBytes: Long): String {
        return getBytes(uri, maxBytes).toString(Charsets.UTF_8)
    }

    /**
     * Downloads [uri] to [target]. The target file is deleted if the download fails.
     */
    fun download(uri: URI, target: Path, maxBytes: Long = MAX_PLUGIN_SIZE) {
        try {
            target.outputStream().use { transfer(uri, it, maxBytes) }
            log.debug { "Downloaded '$uri' to '$target' (${target.fileSize()} bytes)" }
        } catch (e: Exception) {
            Files.deleteIfExists(target)
            throw e
        }
    }

    private fun transfer(uri: URI, output: OutputStream, maxBytes: Long) {
        checkAllowed(uri)

        if (uri.scheme.equals("file", ignoreCase = true)) {
            val path = Path(uri.path)
            if (Files.size(path) > maxBytes) throw IOException("File '$uri' exceeds the maximum size of $maxBytes bytes")
            Files.newInputStream(path).use { copyLimited(it, output, maxBytes, uri) }
            return
        }

        val request = HttpRequest.newBuilder(uri)
            .timeout(requestTimeout)
            .header("User-Agent", userAgent)
            .GET()
            .build()

        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Request to '$uri' was interrupted", e)
        }

        response.body().use { body ->
            if (response.statusCode() !in 200..299) {
                throw IOException("Request to '$uri' failed with HTTP status ${response.statusCode()}")
            }

            val contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1)
            if (contentLength > maxBytes) {
                throw IOException("Response of '$uri' exceeds the maximum size of $maxBytes bytes")
            }

            copyLimited(body, output, maxBytes, uri)
        }
    }

    private fun copyLimited(input: InputStream, output: OutputStream, maxBytes: Long, uri: URI) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) throw IOException("Response of '$uri' exceeds the maximum size of $maxBytes bytes")
            output.write(buffer, 0, read)
        }
    }
}
