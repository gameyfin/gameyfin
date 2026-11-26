package org.gameyfin.app.core.security

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.config.ConfigProperties.SSO.OIDC
import org.gameyfin.app.config.ConfigService
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream

@Component
class OidcAvatarSource(private val config: ConfigService) {

    fun load(oidcUser: OidcUser): ByteArray? {
        val picture = (oidcUser.claims["picture"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            val uri = URI(picture)
            val origin = Origin.from(uri) ?: return null
            // Profile claims can contain user-supplied URLs. Only contact administrator-trusted origins.
            val trusted = sequenceOf(OIDC.IssuerUrl, OIDC.UserInfoUrl, OIDC.AvatarOrigin)
                .mapNotNull { config.get(it) }
                .mapNotNull { runCatching { Origin.from(URI(it.trim())) }.getOrNull() }
            if (origin !in trusted) return null

            val connection = uri.toURL().openConnection() as HttpURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("Accept", "image/png,image/jpeg,image/gif")
                val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TIMEOUT_MS.toLong())
                if (connection.responseCode != HttpURLConnection.HTTP_OK || connection.contentLengthLong > MAX_BYTES) {
                    return null
                }
                val bytes = connection.inputStream.use { readImage(it, deadline) }
                toPng(bytes)
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            // URLs can contain signed query strings, so avoid logging them or the exception message.
            log.debug { "Could not load OIDC avatar (${e.javaClass.simpleName})" }
            null
        }
    }

    private fun readImage(input: InputStream, deadline: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (System.nanoTime() - deadline < 0) {
            val count = input.read(buffer)
            if (count == -1) return output.toByteArray()
            if (output.size() + count > MAX_BYTES) throw IOException("OIDC avatar exceeds size limit")
            output.write(buffer, 0, count)
        }
        throw IOException("OIDC avatar download timed out")
    }

    private fun toPng(bytes: ByteArray): ByteArray? = MemoryCacheImageInputStream(bytes.inputStream()).use { input ->
        val reader = ImageIO.getImageReaders(input).asSequence().firstOrNull() ?: return null
        try {
            reader.setInput(input, true, true)
            if (reader.formatName.lowercase() !in RASTER_FORMATS) return null
            if (reader.getWidth(0) !in 1..MAX_DIMENSION || reader.getHeight(0) !in 1..MAX_DIMENSION) return null

            // Serve a decoded raster image without embedded metadata or trailing data.
            val image = reader.read(0)
            try {
                val output = ByteArrayOutputStream()
                if (ImageIO.write(image, "png", output)) output.toByteArray() else null
            } finally {
                image.flush()
            }
        } finally {
            reader.dispose()
        }
    }

    private data class Origin(val scheme: String, val host: String, val port: Int) {
        companion object {
            fun from(uri: URI): Origin? {
                val scheme = uri.scheme?.lowercase()
                val host = uri.host?.lowercase() ?: return null
                if (uri.userInfo != null || uri.port !in -1..65535) return null
                val defaultPort = when (scheme) {
                    "https" -> 443
                    "http" -> 80
                    else -> return null
                }
                return Origin(scheme, host, if (uri.port == -1) defaultPort else uri.port)
            }
        }
    }

    companion object {
        private val log = KotlinLogging.logger {}
        private val RASTER_FORMATS = setOf("png", "jpeg", "gif")
        private const val TIMEOUT_MS = 3_000
        private const val MAX_BYTES = 2 * 1024 * 1024
        private const val MAX_DIMENSION = 2048
    }
}
