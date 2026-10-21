package org.gameyfin.app.core.plugins.marketplace

import com.sun.net.httpserver.HttpServer
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.test.*

class GameyfinFileDownloaderTest {

    @TempDir
    lateinit var tempDir: Path

    private val httpClient = mockk<MarketplaceHttpClient>()

    @Test
    fun `creates download directory and temp file and delegates to http client`() {
        val downloadDirectory = tempDir.resolve("plugins-installed").resolve(".download")
        val uri = slot<URI>()
        val target = slot<Path>()
        every { httpClient.download(capture(uri), capture(target), any()) } just runs
        val downloader = GameyfinFileDownloader(httpClient) { downloadDirectory }

        val result = downloader.downloadFile(URI("https://example.com/files/plugin-1.0.0.jar").toURL())

        assertTrue(downloadDirectory.isDirectory())
        assertEquals(downloadDirectory, result.parent)
        assertTrue(result.name.startsWith("plugin-"))
        assertTrue(result.name.endsWith(".jar.part"))
        assertTrue(result.exists())
        assertEquals(URI("https://example.com/files/plugin-1.0.0.jar"), uri.captured)
        assertEquals(result, target.captured)
        verify(exactly = 1) { httpClient.download(any(), any(), MarketplaceHttpClient.MAX_PLUGIN_SIZE) }
    }

    @Test
    fun `reuses an existing download directory`() {
        val downloadDirectory = Files.createDirectories(tempDir.resolve(".download"))
        every { httpClient.download(any(), any(), any()) } just runs
        val downloader = GameyfinFileDownloader(httpClient) { downloadDirectory }

        val first = downloader.downloadFile(URI("https://example.com/a.jar").toURL())
        val second = downloader.downloadFile(URI("https://example.com/a.jar").toURL())

        assertNotEquals(first, second)
        assertEquals(setOf(first, second), downloadDirectory.listDirectoryEntries().toSet())
    }

    @Test
    fun `evaluates the download directory on every download`() {
        var current = tempDir.resolve("first")
        every { httpClient.download(any(), any(), any()) } just runs
        val downloader = GameyfinFileDownloader(httpClient) { current }

        val first = downloader.downloadFile(URI("https://example.com/a.jar").toURL())
        current = tempDir.resolve("second")
        val second = downloader.downloadFile(URI("https://example.com/a.jar").toURL())

        assertEquals(tempDir.resolve("first"), first.parent)
        assertEquals(tempDir.resolve("second"), second.parent)
    }

    @Test
    fun `propagates download errors`() {
        val downloadDirectory = tempDir.resolve(".download")
        every { httpClient.download(any(), any(), any()) } throws IOException("HTTP 500")
        val downloader = GameyfinFileDownloader(httpClient) { downloadDirectory }

        val e = assertFailsWith<IOException> { downloader.downloadFile(URI("https://example.com/a.jar").toURL()) }

        assertEquals("HTTP 500", e.message)
    }

    @Test
    fun `downloads file content with real http client`() {
        val body = ByteArray(10_000) { (it % 97).toByte() }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/plugin.jar") { exchange ->
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.write(body)
            exchange.close()
        }
        server.start()
        try {
            val downloadDirectory = tempDir.resolve(".download")
            val downloader = GameyfinFileDownloader(MarketplaceHttpClient(allowInsecureUrls = true)) { downloadDirectory }

            val result = downloader.downloadFile(URI("http://127.0.0.1:${server.address.port}/plugin.jar").toURL())

            assertContentEquals(body, result.readBytes())
            assertEquals(downloadDirectory, result.parent)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `failed download with real http client leaves no temp file`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/plugin.jar") { exchange ->
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
        }
        server.start()
        try {
            val downloadDirectory = tempDir.resolve(".download")
            val downloader = GameyfinFileDownloader(MarketplaceHttpClient(allowInsecureUrls = true)) { downloadDirectory }

            assertFailsWith<IOException> {
                downloader.downloadFile(URI("http://127.0.0.1:${server.address.port}/plugin.jar").toURL())
            }

            assertTrue(downloadDirectory.listDirectoryEntries().isEmpty())
        } finally {
            server.stop(0)
        }
    }
}
