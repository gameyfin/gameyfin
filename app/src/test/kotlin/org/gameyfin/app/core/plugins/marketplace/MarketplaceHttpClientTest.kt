package org.gameyfin.app.core.plugins.marketplace

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.*

class MarketplaceHttpClientTest {

    private lateinit var server: HttpServer
    private val executor = Executors.newCachedThreadPool()
    private lateinit var baseUrl: String

    @TempDir
    lateinit var tempDir: Path

    private val client = MarketplaceHttpClient(
        connectTimeout = Duration.ofSeconds(5),
        requestTimeout = Duration.ofSeconds(10),
        allowInsecureUrls = true,
        applicationVersion = "1.2.3"
    )

    @BeforeEach
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = executor
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}"
    }

    @AfterEach
    fun tearDown() {
        runCatching { server.stop(0) }
        executor.shutdownNow()
    }

    private fun handle(path: String, handler: (HttpExchange) -> Unit) {
        server.createContext(path) { exchange ->
            try {
                handler(exchange)
            } finally {
                exchange.close()
            }
        }
    }

    private fun respond(path: String, status: Int, body: ByteArray, chunked: Boolean = false) {
        handle(path) { exchange ->
            exchange.sendResponseHeaders(status, if (chunked) 0 else if (body.isEmpty()) -1 else body.size.toLong())
            if (body.isNotEmpty()) exchange.responseBody.write(body)
        }
    }

    private fun uri(path: String) = URI("$baseUrl$path")

    // --- Limits ---

    @Test
    fun `size limits have expected values`() {
        assertEquals(5L * 1024 * 1024, MarketplaceHttpClient.MAX_INDEX_SIZE)
        assertEquals(1L * 1024 * 1024, MarketplaceHttpClient.MAX_LOGO_SIZE)
        assertEquals(16L * 1024, MarketplaceHttpClient.MAX_CHECKSUM_SIZE)
        assertEquals(512L * 1024 * 1024, MarketplaceHttpClient.MAX_PLUGIN_SIZE)
    }

    // --- Successful requests ---

    @Test
    fun `getString returns body of 2xx response`() {
        respond("/index.json", 200, """{"plugins":[]}""".toByteArray())
        assertEquals("""{"plugins":[]}""", client.getString(uri("/index.json"), 1024))
    }

    @Test
    fun `getString decodes body as UTF-8`() {
        respond("/utf8", 200, "Grüße ✓".toByteArray(Charsets.UTF_8))
        assertEquals("Grüße ✓", client.getString(uri("/utf8"), 1024))
    }

    @Test
    fun `getBytes returns body of non 200 2xx response`() {
        val body = byteArrayOf(1, 2, 3, 4)
        respond("/bytes", 203, body)
        assertContentEquals(body, client.getBytes(uri("/bytes"), 1024))
    }

    @Test
    fun `getBytes returns chunked body`() {
        val body = ByteArray(200_000) { (it % 251).toByte() }
        respond("/chunked", 200, body, chunked = true)
        assertContentEquals(body, client.getBytes(uri("/chunked"), body.size.toLong()))
    }

    @Test
    fun `body exactly at the limit is accepted`() {
        val body = ByteArray(100) { 7 }
        respond("/exact", 200, body)
        assertContentEquals(body, client.getBytes(uri("/exact"), 100))
    }

    @Test
    fun `empty body is returned as empty array`() {
        respond("/empty", 200, ByteArray(0))
        assertEquals(0, client.getBytes(uri("/empty"), 100).size)
    }

    @Test
    fun `sends Gameyfin user agent`() {
        val userAgent = AtomicReference<String?>()
        handle("/ua") { exchange ->
            userAgent.set(exchange.requestHeaders.getFirst("User-Agent"))
            exchange.sendResponseHeaders(200, 2)
            exchange.responseBody.write("ok".toByteArray())
        }
        client.getString(uri("/ua"), 100)
        assertEquals("Gameyfin/1.2.3 (+https://github.com/gameyfin/gameyfin)", userAgent.get())
    }

    @Test
    fun `uses unknown version in user agent by default`() {
        val userAgent = AtomicReference<String?>()
        handle("/ua") { exchange ->
            userAgent.set(exchange.requestHeaders.getFirst("User-Agent"))
            exchange.sendResponseHeaders(200, 2)
            exchange.responseBody.write("ok".toByteArray())
        }
        MarketplaceHttpClient(allowInsecureUrls = true).getString(uri("/ua"), 100)
        assertEquals("Gameyfin/unknown (+https://github.com/gameyfin/gameyfin)", userAgent.get())
    }

    @Test
    fun `uses GET requests`() {
        val method = AtomicReference<String?>()
        handle("/method") { exchange ->
            method.set(exchange.requestMethod)
            exchange.sendResponseHeaders(200, -1)
        }
        client.getBytes(uri("/method"), 100)
        assertEquals("GET", method.get())
    }

    @Test
    fun `follows redirects`() {
        handle("/old") { exchange ->
            exchange.responseHeaders.add("Location", "$baseUrl/new")
            exchange.sendResponseHeaders(302, -1)
        }
        respond("/new", 200, "moved".toByteArray())
        assertEquals("moved", client.getString(uri("/old"), 100))
    }

    // --- Errors ---

    @Test
    fun `non 2xx response is an error`() {
        respond("/missing", 404, "not found".toByteArray())
        val e = assertFailsWith<IOException> { client.getString(uri("/missing"), 1024) }
        assertTrue(e.message!!.contains("404"))
    }

    @Test
    fun `server error response is an error`() {
        respond("/error", 500, ByteArray(0))
        val e = assertFailsWith<IOException> { client.getBytes(uri("/error"), 1024) }
        assertTrue(e.message!!.contains("500"))
    }

    @Test
    fun `response with content length over the limit is an error`() {
        respond("/large", 200, ByteArray(101))
        val e = assertFailsWith<IOException> { client.getBytes(uri("/large"), 100) }
        assertTrue(e.message!!.contains("exceeds the maximum size of 100 bytes"))
    }

    @Test
    fun `chunked response over the limit is an error`() {
        respond("/large-chunked", 200, ByteArray(300_000), chunked = true)
        val e = assertFailsWith<IOException> { client.getBytes(uri("/large-chunked"), 100_000) }
        assertTrue(e.message!!.contains("exceeds the maximum size of 100000 bytes"))
    }

    @Test
    fun `request timeout is an error`() {
        val release = CountDownLatch(1)
        handle("/slow") { exchange ->
            release.await(10, TimeUnit.SECONDS)
            exchange.sendResponseHeaders(200, -1)
        }
        val slowClient = MarketplaceHttpClient(requestTimeout = Duration.ofMillis(200), allowInsecureUrls = true)
        try {
            assertFailsWith<IOException> { slowClient.getBytes(uri("/slow"), 100) }
        } finally {
            release.countDown()
        }
    }

    @Test
    fun `connection error is an error`() {
        val port = server.address.port
        server.stop(0)
        assertFailsWith<IOException> { client.getBytes(URI("http://127.0.0.1:$port/gone"), 100) }
    }

    // --- Scheme checks ---

    @Test
    fun `http is rejected unless insecure URLs are allowed`() {
        val called = AtomicBoolean(false)
        handle("/secure") { exchange ->
            called.set(true)
            exchange.sendResponseHeaders(200, -1)
        }
        val secureClient = MarketplaceHttpClient()
        assertFalse(secureClient.allowInsecureUrls)
        assertFailsWith<IllegalArgumentException> { secureClient.getBytes(uri("/secure"), 100) }
        assertFalse(called.get())
    }

    @Test
    fun `checkAllowed accepts only https by default`() {
        val secureClient = MarketplaceHttpClient()
        secureClient.checkAllowed(URI("https://example.com/a.jar"))
        secureClient.checkAllowed(URI("HTTPS://example.com/a.jar"))
        assertFailsWith<IllegalArgumentException> { secureClient.checkAllowed(URI("http://example.com/a.jar")) }
        assertFailsWith<IllegalArgumentException> { secureClient.checkAllowed(URI("file:///a.jar")) }
        assertFailsWith<IllegalArgumentException> { secureClient.checkAllowed(URI("ftp://example.com/a.jar")) }
        assertFailsWith<IllegalArgumentException> { secureClient.checkAllowed(URI("relative/a.jar")) }
    }

    @Test
    fun `checkAllowed accepts http and file when insecure URLs are allowed`() {
        client.checkAllowed(URI("https://example.com/a.jar"))
        client.checkAllowed(URI("http://example.com/a.jar"))
        client.checkAllowed(URI("file:///a.jar"))
        assertFailsWith<IllegalArgumentException> { client.checkAllowed(URI("ftp://example.com/a.jar")) }
        assertFailsWith<IllegalArgumentException> { client.checkAllowed(URI("relative/a.jar")) }
    }

    // --- file:// URLs ---

    @Test
    fun `reads file URLs when insecure URLs are allowed`() {
        val file = tempDir.resolve("plugins.json")
        file.writeText("""[{"id":"a"}]""")
        assertEquals("""[{"id":"a"}]""", client.getString(file.toUri(), 1024))
    }

    @Test
    fun `file URL is rejected unless insecure URLs are allowed`() {
        val file = tempDir.resolve("plugins.json")
        file.writeText("[]")
        assertFailsWith<IllegalArgumentException> { MarketplaceHttpClient().getString(file.toUri(), 1024) }
    }

    @Test
    fun `file over the limit is an error`() {
        val file = tempDir.resolve("large.bin")
        file.writeBytes(ByteArray(101))
        val e = assertFailsWith<IOException> { client.getBytes(file.toUri(), 100) }
        assertTrue(e.message!!.contains("exceeds the maximum size of 100 bytes"))
    }

    @Test
    fun `missing file is an error`() {
        assertFailsWith<IOException> { client.getBytes(tempDir.resolve("missing.json").toUri(), 100) }
    }

    // --- download ---

    @Test
    fun `download writes body to target`() {
        val body = ByteArray(50_000) { (it % 13).toByte() }
        respond("/plugin.jar", 200, body)
        val target = tempDir.resolve("plugin.jar.part")

        client.download(uri("/plugin.jar"), target)

        assertContentEquals(body, target.readBytes())
    }

    @Test
    fun `download overwrites existing target`() {
        respond("/plugin.jar", 200, "new".toByteArray())
        val target = tempDir.resolve("plugin.jar.part")
        target.writeText("old content that is longer")

        client.download(uri("/plugin.jar"), target, 100)

        assertEquals("new", Files.readString(target))
    }

    @Test
    fun `download from file URL copies the file`() {
        val source = tempDir.resolve("source.jar")
        source.writeBytes(byteArrayOf(9, 8, 7))
        val target = tempDir.resolve("target.jar.part")

        client.download(source.toUri(), target, 100)

        assertContentEquals(byteArrayOf(9, 8, 7), target.readBytes())
    }

    @Test
    fun `download deletes target on HTTP error`() {
        respond("/plugin.jar", 500, ByteArray(0))
        val target = tempDir.resolve("plugin.jar.part")

        assertFailsWith<IOException> { client.download(uri("/plugin.jar"), target) }

        assertFalse(target.exists())
    }

    @Test
    fun `download deletes target when size limit is exceeded while streaming`() {
        respond("/plugin.jar", 200, ByteArray(300_000), chunked = true)
        val target = tempDir.resolve("plugin.jar.part")

        assertFailsWith<IOException> { client.download(uri("/plugin.jar"), target, 100_000) }

        assertFalse(target.exists())
    }

    @Test
    fun `download deletes pre existing target on disallowed scheme`() {
        val target = tempDir.resolve("plugin.jar.part")
        target.writeText("partial")

        assertFailsWith<IllegalArgumentException> {
            MarketplaceHttpClient().download(uri("/plugin.jar"), target)
        }

        assertFalse(target.exists())
    }
}
