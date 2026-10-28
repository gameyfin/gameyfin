package org.gameyfin.app.api.v1

import io.mockk.*
import org.gameyfin.app.core.download.files.DownloadService
import org.gameyfin.app.core.download.provider.DownloadProviderDto
import org.gameyfin.app.games.GameService
import org.gameyfin.app.media.Image
import org.gameyfin.app.media.ImageService
import org.gameyfin.app.media.ImageType
import org.gameyfin.pluginapi.download.FileDownload
import org.gameyfin.pluginapi.download.LinkDownload
import org.junit.jupiter.api.Test
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Files
import kotlin.test.*
import org.gameyfin.app.api.v1.dto.*

class DownloadControllerV1Test {
    private val access = allowingAccess()
    private val downloads = mockk<DownloadService>()
    private val catalog = mockk<CatalogServiceV1>()
    private val games = mockk<GameService>(relaxed = true)
    private val images = mockk<ImageService>()
    private val controller = DownloadControllerV1(access, downloads, catalog, games, images)

    private fun provider(key: String = "direct", priority: Int = 1) =
        DownloadProviderDto(key, "Provider $key", priority, "Description $key")

    @Test
    fun `providers are sorted by descending priority`() {
        every { downloads.getProviders() } returns listOf(provider("low", 1), provider("high", 10))

        val result = controller.providers()

        assertEquals(listOf("high", "low"), result.map { it.id })
        assertEquals(DownloadProviderV1("high", "Provider high", "Description high", 10), result.first())
    }

    @Test
    fun `file downloads stream with sanitized filename and attribution`() {
        val game = testGame(title = "Bad/Title:*?")
        val data = mockk<InputStream>(relaxed = true)
        every { catalog.gameEntity("10") } returns game
        every { downloads.getProviders() } returns listOf(provider())
        every { downloads.getDownload(game.metadata.path, "direct") } returns FileDownload(data, "zip", 42)
        every { downloads.processDownload(any(), any(), any(), any(), any(), any()) } just runs
        val request = MockHttpServletRequest().apply { remoteAddr = "10.0.0.1" }

        val response = controller.download("10", "direct", request)

        assertEquals(200, response.statusCode.value())
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, response.headers.contentType)
        assertEquals(42, response.headers.contentLength)
        assertEquals("no-store", response.headers.cacheControl)
        assertEquals("BadTitle.zip", response.headers.contentDisposition.filename)
        verify { games.incrementDownloadCount(game) }

        (response.body as StreamingResponseBody).writeTo(ByteArrayOutputStream())
        verify { downloads.processDownload(data, any(), game, "api-key:cred", "api:cred", "10.0.0.1") }
    }

    @Test
    fun `file downloads without title extension or size fall back to defaults`() {
        val game = testGame(title = null)
        every { catalog.gameEntity("10") } returns game
        every { downloads.getProviders() } returns listOf(provider())
        every { downloads.getDownload(any(), any()) } returns FileDownload(mockk(relaxed = true))

        val response = controller.download("10", "direct", MockHttpServletRequest())

        assertEquals("download", response.headers.contentDisposition.filename)
        assertEquals(-1, response.headers.contentLength)
    }

    @Test
    fun `file download stream is closed when preparing the response fails`() {
        val game = testGame()
        val data = mockk<InputStream>(relaxed = true)
        every { catalog.gameEntity("10") } returns game
        every { downloads.getProviders() } returns listOf(provider())
        every { downloads.getDownload(any(), any()) } returns FileDownload(data)
        every { games.incrementDownloadCount(game) } throws IllegalStateException("boom")

        assertFailsWith<IllegalStateException> { controller.download("10", "direct", MockHttpServletRequest()) }
        verify { data.close() }
    }

    @Test
    fun `link downloads return a descriptor`() {
        val game = testGame()
        every { catalog.gameEntity("10") } returns game
        every { downloads.getProviders() } returns listOf(provider())
        every { downloads.getDownload(any(), any()) } returns LinkDownload("https://example.test/game.zip")

        val response = controller.download("10", "direct", MockHttpServletRequest())

        assertEquals(DownloadLinkV1(url = "https://example.test/game.zip"), response.body)
        assertEquals("link", (response.body as DownloadLinkV1).kind)
        assertEquals(MediaType.APPLICATION_JSON, response.headers.contentType)
        verify { games.incrementDownloadCount(game) }
    }

    @Test
    fun `unknown providers are rejected`() {
        every { catalog.gameEntity("10") } returns testGame()
        every { downloads.getProviders() } returns listOf(provider())

        assertFailsWith<IllegalArgumentException> {
            controller.download("10", "missing", MockHttpServletRequest())
        }
        verify(exactly = 0) { downloads.getDownload(any(), any()) }
    }

    @Test
    fun `images are served with caching headers and support conditional requests`() {
        val file = Files.createTempFile("api-image", ".png").also { Files.writeString(it, "png") }
        try {
            val cover = Image(id = 5, type = ImageType.COVER, contentId = "abc", contentLength = 3, mimeType = "image/png")
            val game = testGame().apply { coverImage = cover }
            every { catalog.gameEntity("10") } returns game
            every { images.getFilePath(cover) } returns file

            val response = controller.image("10", "5", ServletWebRequest(MockHttpServletRequest()))
            assertEquals(200, response.statusCode.value())
            assertEquals("\"abc\"", response.headers.eTag)
            assertEquals(MediaType.IMAGE_PNG, response.headers.contentType)
            assertEquals(3, response.headers.contentLength)
            assertEquals("X-API-Key", response.headers.getFirst(HttpHeaders.VARY))
            assertEquals("nosniff", response.headers.getFirst("X-Content-Type-Options"))
            assertEquals(file.toString(), (response.body as FileSystemResource).path)

            val conditional = MockHttpServletRequest("GET", "/v1/games/10/images/5")
                .apply { addHeader(HttpHeaders.IF_NONE_MATCH, "\"abc\"") }
            val notModified = controller.image("10", "5", ServletWebRequest(conditional))
            assertEquals(304, notModified.statusCode.value())
            assertNull(notModified.body)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `gallery images without metadata are served without optional headers`() {
        val file = Files.createTempFile("api-image", ".bin")
        try {
            val screenshot = Image(id = 6, type = ImageType.SCREENSHOT)
            val game = testGame().apply { images = mutableListOf(screenshot) }
            every { catalog.gameEntity("10") } returns game
            every { images.getFilePath(screenshot) } returns file

            val response = controller.image("10", "6", ServletWebRequest(MockHttpServletRequest()))

            assertEquals(200, response.statusCode.value())
            assertNull(response.headers.eTag)
            assertNull(response.headers.contentType)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `images must belong to the game and have content`() {
        val header = Image(id = 7, type = ImageType.HEADER)
        every { catalog.gameEntity("10") } returns testGame().apply { headerImage = header }
        every { images.getFilePath(header) } returns null

        assertFailsWith<NoSuchElementException> {
            controller.image("10", "99", ServletWebRequest(MockHttpServletRequest()))
        }
        assertFailsWith<NoSuchElementException> {
            controller.image("10", "7", ServletWebRequest(MockHttpServletRequest()))
        }
    }
}
