package org.gameyfin.app.core.security

import com.sun.net.httpserver.HttpServer
import io.mockk.every
import io.mockk.mockk
import org.gameyfin.app.config.ConfigProperties.SSO.OIDC
import org.gameyfin.app.config.ConfigService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OidcAvatarSourceTest {
    private lateinit var server: HttpServer
    private lateinit var origin: String
    private val config = mockk<ConfigService>()
    private val source = OidcAvatarSource(config)
    private val pictureRequests = AtomicInteger()

    @BeforeEach
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        origin = "http://127.0.0.1:${server.address.port}"
        server.createContext("/picture") { exchange ->
            pictureRequests.incrementAndGet()
            val jpeg = ByteArrayOutputStream().apply {
                ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "jpeg", this)
            }.toByteArray()
            exchange.sendResponseHeaders(200, jpeg.size.toLong())
            exchange.responseBody.use { it.write(jpeg) }
        }
        server.createContext("/redirect") { exchange ->
            exchange.responseHeaders.set("Location", "$origin/picture")
            exchange.sendResponseHeaders(302, -1)
            exchange.close()
        }
        server.createContext("/invalid") { exchange ->
            val body = "<svg/>".toByteArray()
            exchange.responseHeaders.set("Content-Type", "image/png")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        every { config.get(OIDC.IssuerUrl) } returns "$origin/issuer"
        every { config.get(OIDC.UserInfoUrl) } returns null
        every { config.get(OIDC.AvatarOrigin) } returns null
    }

    @AfterEach
    fun stopServer() {
        server.stop(0)
    }

    @Test
    fun `loads picture from the configured avatar origin`() {
        every { config.get(OIDC.IssuerUrl) } returns "https://issuer.example"
        every { config.get(OIDC.AvatarOrigin) } returns origin

        val png = assertNotNull(source.load(user("$origin/picture")))
        assertEquals(2, ImageIO.read(png.inputStream()).width)
        assertEquals(1, pictureRequests.get())
    }

    @Test
    fun `rejects untrusted origins and redirects before fetching the picture`() {
        assertNull(source.load(user("http://localhost:${server.address.port}/picture")))
        assertNull(source.load(user("$origin/redirect")))
        assertEquals(0, pictureRequests.get())
    }

    @Test
    fun `skips missing malformed and non-image pictures`() {
        assertNull(source.load(user(null)))
        assertNull(source.load(user(listOf("$origin/picture"))))
        assertNull(source.load(user("$origin/invalid")))
    }

    private fun user(picture: Any?): OidcUser = mockk {
        every { claims } returns if (picture == null) emptyMap() else mapOf("picture" to picture)
    }
}
