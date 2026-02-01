package org.gameyfin.app.core.download.files

import io.mockk.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import org.gameyfin.app.core.getRemoteIp
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.games.GameService
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.entities.GameMetadata
import org.gameyfin.pluginapi.download.FileDownload
import org.gameyfin.pluginapi.download.LinkDownload
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.context.request.async.DeferredResult
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DownloadEndpointTest {

    private lateinit var downloadService: DownloadService
    private lateinit var gameService: GameService
    private lateinit var endpoint: DownloadEndpoint
    private lateinit var request: HttpServletRequest
    private lateinit var session: HttpSession

    @BeforeEach
    fun setup() {
        downloadService = mockk<DownloadService>(relaxed = true)
        gameService = mockk<GameService>(relaxed = true)
        endpoint = DownloadEndpoint(downloadService, gameService)

        request = mockk<HttpServletRequest>(relaxed = true)
        session = mockk<HttpSession>(relaxed = true)

        every { request.session } returns session
        every { session.id } returns "test-session-123"
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    /**
     * Helper method to wait for DeferredResult to complete and get the result.
     * Handles async processing with timeout.
     */
    private fun <T : Any> awaitDeferredResult(deferredResult: DeferredResult<T>, timeoutSeconds: Long = 5): T {
        val latch = CountDownLatch(1)
        var result: T? = null
        var error: Throwable? = null

        deferredResult.setResultHandler { value ->
            @Suppress("UNCHECKED_CAST")
            result = value as T
            latch.countDown()
        }

        deferredResult.onError { throwable ->
            error = throwable
            latch.countDown()
        }

        val completed = latch.await(timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) {
            throw AssertionError("DeferredResult did not complete within $timeoutSeconds seconds")
        }

        error?.let { throw AssertionError("DeferredResult completed with error", it) }
        return result ?: throw AssertionError("DeferredResult completed but result is null")
    }

    @Test
    fun `downloadGame should return file download with correct headers`() {
        val gameId = 1L
        val provider = "TestProvider"
        val gamePath = "/path/to/game"
        val game = createTestGame(gameId, "Test Game", gamePath, fileSize = 1024L)
        val inputStream = ByteArrayInputStream("test data".toByteArray())
        val fileDownload = FileDownload(inputStream, "zip")

        mockkStatic("org.gameyfin.app.core.UtilsKt")
        every { request.getRemoteIp(any()) } returns "192.168.1.1"

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns mockk {
            every { name } returns "testuser"
        }

        every { gameService.getById(gameId) } returns game
        every { gameService.incrementDownloadCount(game) } just Runs
        every { downloadService.getDownload(gamePath, provider) } returns fileDownload
        every { downloadService.processDownload(any(), any(), any(), any(), any(), any()) } just Runs

        val deferredResult = endpoint.downloadGame(gameId, provider, request)
        val response = awaitDeferredResult(deferredResult)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.headers.containsHeader("Content-Disposition"))
        assertTrue(response.headers["Content-Disposition"]!![0].contains("Test Game.zip"))

        verify(exactly = 1) { gameService.getById(gameId) }
        verify(exactly = 1) { gameService.incrementDownloadCount(game) }
        verify(exactly = 1) { downloadService.getDownload(gamePath, provider) }
    }

    @Test
    fun `downloadGame should not include Content-Length for directories`() {
        val gameId = 1L
        val provider = "TestProvider"

        // Create a temporary directory path for testing
        val dirPath = System.getProperty("java.io.tmpdir")
        val game = createTestGame(gameId, "Test Game", dirPath, fileSize = 1024L)
        val inputStream = ByteArrayInputStream("test data".toByteArray())
        val fileDownload = FileDownload(inputStream, "zip")

        mockkStatic("org.gameyfin.app.core.UtilsKt")
        every { request.getRemoteIp(any()) } returns "192.168.1.1"

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns null

        every { gameService.getById(gameId) } returns game
        every { gameService.incrementDownloadCount(game) } just Runs
        every { downloadService.getDownload(dirPath, provider) } returns fileDownload
        every { downloadService.processDownload(any(), any(), any(), any(), any(), any()) } just Runs

        val deferredResult = endpoint.downloadGame(gameId, provider, request)
        val response = awaitDeferredResult(deferredResult)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertTrue(response.headers.containsHeader("Content-Disposition"))
        // Content-Length should not be present for directories
        assertFalse(response.headers.containsHeader("Content-Length"))
    }

    @Test
    fun `downloadGame should not include Content-Length when fileSize is null`() {
        val gameId = 1L
        val provider = "TestProvider"
        val gamePath = "/path/to/game"
        val game = createTestGame(gameId, "Test Game", gamePath, fileSize = null)
        val inputStream = ByteArrayInputStream("test data".toByteArray())
        val fileDownload = FileDownload(inputStream, "zip")

        mockkStatic("org.gameyfin.app.core.UtilsKt")
        every { request.getRemoteIp(any()) } returns "192.168.1.1"

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns null

        every { gameService.getById(gameId) } returns game
        every { gameService.incrementDownloadCount(game) } just Runs
        every { downloadService.getDownload(gamePath, provider) } returns fileDownload
        every { downloadService.processDownload(any(), any(), any(), any(), any(), any()) } just Runs

        val deferredResult = endpoint.downloadGame(gameId, provider, request)
        val response = awaitDeferredResult(deferredResult)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertFalse(response.headers.containsHeader("Content-Length"))
    }

    @Test
    fun `downloadGame should not include Content-Length when fileSize is zero`() {
        val gameId = 1L
        val provider = "TestProvider"
        val gamePath = "/path/to/game"
        val game = createTestGame(gameId, "Test Game", gamePath, fileSize = 0L)
        val inputStream = ByteArrayInputStream("test data".toByteArray())
        val fileDownload = FileDownload(inputStream, "zip")

        mockkStatic("org.gameyfin.app.core.UtilsKt")
        every { request.getRemoteIp(any()) } returns "192.168.1.1"

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns null

        every { gameService.getById(gameId) } returns game
        every { gameService.incrementDownloadCount(game) } just Runs
        every { downloadService.getDownload(gamePath, provider) } returns fileDownload
        every { downloadService.processDownload(any(), any(), any(), any(), any(), any()) } just Runs

        val deferredResult = endpoint.downloadGame(gameId, provider, request)
        val response = awaitDeferredResult(deferredResult)

        assertEquals(HttpStatus.OK, response.statusCode)
        assertFalse(response.headers.containsHeader("Content-Length"))
    }

    @Test
    fun `downloadGame should handle authenticated user`() {
        val gameId = 1L
        val provider = "TestProvider"
        val gamePath = "/path/to/game"
        val game = createTestGame(gameId, "Test Game", gamePath, fileSize = 1024L)
        val inputStream = ByteArrayInputStream("test data".toByteArray())
        val fileDownload = FileDownload(inputStream, "zip")

        val authMock = mockk<org.springframework.security.core.Authentication>()
        every { authMock.name } returns "authenticatedUser"

        mockkStatic("org.gameyfin.app.core.UtilsKt")
        every { request.getRemoteIp(any()) } returns "192.168.1.1"

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns authMock

        every { gameService.getById(gameId) } returns game
        every { gameService.incrementDownloadCount(game) } just Runs
        every { downloadService.getDownload(gamePath, provider) } returns fileDownload
        every { downloadService.processDownload(any(), any(), any(), any(), any(), any()) } just Runs

        val deferredResult = endpoint.downloadGame(gameId, provider, request)
        val response = awaitDeferredResult(deferredResult)

        assertEquals(HttpStatus.OK, response.statusCode)

        // Execute the streaming body to verify processDownload is called
        val outputStream = ByteArrayOutputStream()
        response.body?.writeTo(outputStream)

        verify(exactly = 1) {
            downloadService.processDownload(
                inputStream,
                any(),
                game,
                "authenticatedUser",
                "test-session-123",
                "192.168.1.1"
            )
        }
    }

    @Test
    fun `downloadGame should handle anonymous user`() {
        val gameId = 1L
        val provider = "TestProvider"
        val gamePath = "/path/to/game"
        val game = createTestGame(gameId, "Test Game", gamePath, fileSize = 1024L)
        val inputStream = ByteArrayInputStream("test data".toByteArray())
        val fileDownload = FileDownload(inputStream, "zip")

        mockkStatic("org.gameyfin.app.core.UtilsKt")
        every { request.getRemoteIp(any()) } returns "192.168.1.1"

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns null

        every { gameService.getById(gameId) } returns game
        every { gameService.incrementDownloadCount(game) } just Runs
        every { downloadService.getDownload(gamePath, provider) } returns fileDownload
        every { downloadService.processDownload(any(), any(), any(), any(), any(), any()) } just Runs

        val deferredResult = endpoint.downloadGame(gameId, provider, request)
        val response = awaitDeferredResult(deferredResult)

        assertEquals(HttpStatus.OK, response.statusCode)

        val outputStream = ByteArrayOutputStream()
        response.body?.writeTo(outputStream)

        verify(exactly = 1) {
            downloadService.processDownload(
                inputStream,
                any(),
                game,
                null,
                "test-session-123",
                "192.168.1.1"
            )
        }
    }

    @Test
    fun `downloadGame should increment download count`() {
        val gameId = 1L
        val provider = "TestProvider"
        val gamePath = "/path/to/game"
        val game = createTestGame(gameId, "Test Game", gamePath, fileSize = 1024L)
        val inputStream = ByteArrayInputStream("test data".toByteArray())
        val fileDownload = FileDownload(inputStream, "zip")

        mockkStatic("org.gameyfin.app.core.UtilsKt")
        every { request.getRemoteIp(any()) } returns "192.168.1.1"

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns null

        every { gameService.getById(gameId) } returns game
        every { gameService.incrementDownloadCount(game) } just Runs
        every { downloadService.getDownload(gamePath, provider) } returns fileDownload
        every { downloadService.processDownload(any(), any(), any(), any(), any(), any()) } just Runs

        val deferredResult = endpoint.downloadGame(gameId, provider, request)
        awaitDeferredResult(deferredResult)

        verify(exactly = 1) { gameService.incrementDownloadCount(game) }
    }

    @Test
    fun `downloadGame should throw TODO for LinkDownload`() {
        val gameId = 1L
        val provider = "TestProvider"
        val gamePath = "/path/to/game"
        val game = createTestGame(gameId, "Test Game", gamePath, fileSize = 1024L)
        val linkDownload = LinkDownload("https://example.com/download")

        mockkStatic("org.gameyfin.app.core.UtilsKt")
        every { request.getRemoteIp(any()) } returns "192.168.1.1"

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns null

        every { gameService.getById(gameId) } returns game
        every { gameService.incrementDownloadCount(game) } just Runs
        every { downloadService.getDownload(gamePath, provider) } returns linkDownload

        val deferredResult = endpoint.downloadGame(gameId, provider, request)

        assertThrows(AssertionError::class.java) {
            awaitDeferredResult(deferredResult)
        }
    }

    @Test
    fun `downloadGame should remove common invalid chars from filename`() {
        val gameId = 1L
        val provider = "TestProvider"
        val gamePath = "/path/to/game"
        val game = createTestGame(gameId, "Test: Game (2024) [Edition]", gamePath, fileSize = 1024L)
        val inputStream = ByteArrayInputStream("test data".toByteArray())
        val fileDownload = FileDownload(inputStream, "zip")

        mockkStatic("org.gameyfin.app.core.UtilsKt")
        every { request.getRemoteIp(any()) } returns "192.168.1.1"

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns null

        every { gameService.getById(gameId) } returns game
        every { gameService.incrementDownloadCount(game) } just Runs
        every { downloadService.getDownload(gamePath, provider) } returns fileDownload
        every { downloadService.processDownload(any(), any(), any(), any(), any(), any()) } just Runs

        val deferredResult = endpoint.downloadGame(gameId, provider, request)
        val response = awaitDeferredResult(deferredResult)

        assertEquals(HttpStatus.OK, response.statusCode)
        val contentDisposition = response.headers["Content-Disposition"]!![0]
        assertTrue(contentDisposition.contains("Test Game (2024) [Edition].zip")) // ":" should be removed since most filesystems don't allow it
    }

    @Test
    fun `downloadGame should handle different file extensions`() {
        val testCases = listOf("zip", "7z", "rar", "tar", "gz", "exe", "iso")

        testCases.forEach { extension ->
            clearAllMocks(answers = false)

            val gameId = 1L
            val provider = "TestProvider"
            val gamePath = "/path/to/game"
            val game = createTestGame(gameId, "Test Game", gamePath, fileSize = 1024L)
            val inputStream = ByteArrayInputStream("test data".toByteArray())
            val fileDownload = FileDownload(inputStream, extension)

            mockkStatic("org.gameyfin.app.core.UtilsKt")
            every { request.getRemoteIp(any()) } returns "192.168.1.1"

            mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
            every { getCurrentAuth() } returns null

            every { gameService.getById(gameId) } returns game
            every { gameService.incrementDownloadCount(game) } just Runs
            every { downloadService.getDownload(gamePath, provider) } returns fileDownload
            every { downloadService.processDownload(any(), any(), any(), any(), any(), any()) } just Runs

            val deferredResult = endpoint.downloadGame(gameId, provider, request)
            val response = awaitDeferredResult(deferredResult)

            val contentDisposition = response.headers["Content-Disposition"]!![0]
            assertTrue(
                contentDisposition.contains("Test Game.$extension"),
                "Expected extension .$extension in: $contentDisposition"
            )
        }
    }

    @Test
    fun `downloadGame should handle session without id`() {
        val gameId = 1L
        val provider = "TestProvider"
        val gamePath = "/path/to/game"
        val game = createTestGame(gameId, "Test Game", gamePath, fileSize = 1024L)
        val inputStream = ByteArrayInputStream("test data".toByteArray())
        val fileDownload = FileDownload(inputStream, "zip")

        every { session.id } returns "null-session"

        mockkStatic("org.gameyfin.app.core.UtilsKt")
        every { request.getRemoteIp(any()) } returns "192.168.1.1"

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { getCurrentAuth() } returns null

        every { gameService.getById(gameId) } returns game
        every { gameService.incrementDownloadCount(game) } just Runs
        every { downloadService.getDownload(gamePath, provider) } returns fileDownload
        every { downloadService.processDownload(any(), any(), any(), any(), any(), any()) } just Runs

        val deferredResult = endpoint.downloadGame(gameId, provider, request)
        val response = awaitDeferredResult(deferredResult)

        assertEquals(HttpStatus.OK, response.statusCode)
    }

    @Test
    fun `should be annotated correctly`() {
        val annotations = DownloadEndpoint::class.annotations
        assertTrue(annotations.any { it.annotationClass.simpleName == "RestController" })
        assertTrue(annotations.any { it.annotationClass.simpleName == "RequestMapping" })
        assertTrue(annotations.any { it.annotationClass.simpleName == "DynamicPublicAccess" })
        assertTrue(annotations.any { it.annotationClass.simpleName == "AnonymousAllowed" })
    }

    private fun createTestGame(id: Long, title: String, path: String, fileSize: Long?): Game {
        val metadata = mockk<GameMetadata>()
        every { metadata.path } returns path
        every { metadata.fileSize } returns fileSize

        val game = mockk<Game>()
        every { game.id } returns id
        every { game.title } returns title
        every { game.metadata } returns metadata

        return game
    }
}

