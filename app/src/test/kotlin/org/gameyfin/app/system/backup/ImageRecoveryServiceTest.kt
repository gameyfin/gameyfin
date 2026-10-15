package org.gameyfin.app.system.backup

import com.sun.net.httpserver.HttpServer
import io.mockk.*
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.entities.GameFieldMetadata
import org.gameyfin.app.games.entities.GameMetadata
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.games.repositories.ImageRepository
import org.gameyfin.app.media.Image
import org.gameyfin.app.media.ImageService
import org.gameyfin.app.media.ImageType
import org.gameyfin.app.users.entities.User
import org.gameyfin.app.users.persistence.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.transaction.PlatformTransactionManager
import java.io.InputStream
import java.net.InetSocketAddress
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageRecoveryServiceTest {

    companion object {
        private val IMAGE_CONTENT = byteArrayOf(1, 2, 3, 4)
    }

    @TempDir
    lateinit var backupDirectory: Path

    private lateinit var server: HttpServer
    private lateinit var imageRepository: ImageRepository
    private lateinit var imageService: ImageService
    private lateinit var gameRepository: GameRepository
    private lateinit var userRepository: UserRepository
    private lateinit var service: ImageRecoveryService

    @BeforeEach
    fun setup() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/available") { exchange ->
                exchange.sendResponseHeaders(200, IMAGE_CONTENT.size.toLong())
                exchange.responseBody.use { it.write(IMAGE_CONTENT) }
            }
            createContext("/gone") { exchange ->
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            }
            createContext("/error") { exchange ->
                exchange.sendResponseHeaders(503, -1)
                exchange.close()
            }
            start()
        }

        imageRepository = mockk()
        imageService = mockk()
        gameRepository = mockk()
        userRepository = mockk()
        every { imageService.deleteImageIfUnused(any()) } just Runs
        every { gameRepository.findAllByImage(any()) } returns emptyList()
        every { userRepository.findAllByAvatar(any()) } returns emptyList()

        service = ImageRecoveryService(
            imageRepository,
            imageService,
            gameRepository,
            userRepository,
            mockk<PlatformTransactionManager>(relaxed = true),
            backupDirectory.toString()
        )
    }

    @AfterEach
    fun tearDown() {
        service.shutdown()
        server.stop(0)
        clearAllMocks()
    }

    private fun url(path: String) = "http://127.0.0.1:${server.address.port}$path"

    private fun awaitFinished(): ImageRecoveryProgress =
        service.subscribeToProgressEvents()
            .flatMapIterable { it }
            .filter { it.status != BackupTaskStatus.IN_PROGRESS }
            .blockFirst(Duration.ofSeconds(20))!!

    @Test
    fun `restores missing images and removes images which are not available anymore`() {
        val available = Image(id = 1, originalUrl = url("/available/cover.png"), type = ImageType.COVER)
        val gone = Image(id = 2, originalUrl = url("/gone/header.png"), type = ImageType.HEADER)
        val goneScreenshot = Image(id = 3, originalUrl = url("/gone/screenshot.png"), type = ImageType.SCREENSHOT)
        val temporarilyUnavailable = Image(id = 4, originalUrl = url("/error/cover.png"), type = ImageType.COVER)
        val uploadedAvatar = Image(id = 5, type = ImageType.AVATAR)
        val present = Image(id = 6, originalUrl = url("/available/present.png"), type = ImageType.COVER)
        val images = listOf(available, gone, goneScreenshot, temporarilyUnavailable, uploadedAvatar, present)

        val game = Game(library = mockk(relaxed = true), metadata = GameMetadata(path = "/games/game"))
        game.coverImage = available
        game.headerImage = gone
        game.images = mutableListOf(goneScreenshot)
        game.metadata.fields = mutableMapOf(
            "coverImage" to mockk<GameFieldMetadata>(),
            "headerImage" to mockk<GameFieldMetadata>(),
            "images" to mockk<GameFieldMetadata>()
        )
        val user = User(username = "user", email = "user@example.com").apply { avatar = uploadedAvatar }

        every { imageRepository.findAll() } returns images
        every { imageService.hasContent(any()) } answers { firstArg<Image>() === present }
        val restoredContent = mutableListOf<ByteArray>()
        every { imageService.restoreContent(available, any()) } answers {
            restoredContent += secondArg<InputStream>().readAllBytes()
            available
        }
        every { gameRepository.findAllByImage(2) } returns listOf(game)
        every { gameRepository.findAllByImage(3) } returns listOf(game)
        every { userRepository.findAllByAvatar(5) } returns listOf(user)

        BackupStorage(backupDirectory).scheduleImageRecovery()
        service.onApplicationReady()
        val result = awaitFinished()

        assertEquals(BackupTaskStatus.COMPLETED, result.status)
        assertEquals(1, result.restored)
        assertEquals(3, result.removed)
        assertEquals(1, result.failed)
        assertContentEquals(IMAGE_CONTENT, restoredContent.single())

        assertEquals(available, game.coverImage)
        assertNull(game.headerImage)
        assertTrue(game.images.isEmpty())
        assertEquals(setOf("coverImage"), game.metadata.fields.keys)
        assertNull(user.avatar)

        verify(exactly = 1) { imageService.deleteImageIfUnused(gone) }
        verify(exactly = 1) { imageService.deleteImageIfUnused(goneScreenshot) }
        verify(exactly = 1) { imageService.deleteImageIfUnused(uploadedAvatar) }
        verify(exactly = 0) { imageService.deleteImageIfUnused(temporarilyUnavailable) }
        verify(exactly = 0) { imageService.restoreContent(present, any()) }

        awaitMarkerCleared()
    }

    @Test
    fun `does not publish a task if no images are missing`() {
        every { imageRepository.findAll() } returns listOf(Image(id = 1, type = ImageType.COVER))
        every { imageService.hasContent(any()) } returns true

        BackupStorage(backupDirectory).scheduleImageRecovery()
        service.onApplicationReady()

        awaitMarkerCleared()
        val events = service.subscribeToProgressEvents().take(Duration.ofMillis(1500)).collectList().block()!!
        assertTrue(events.flatten().isEmpty())
    }

    @Test
    fun `does nothing if no recovery has been scheduled`() {
        service.onApplicationReady()

        TimeUnit.MILLISECONDS.sleep(200)
        verify(exactly = 0) { imageRepository.findAll() }
    }

    private fun awaitMarkerCleared() {
        val storage = BackupStorage(backupDirectory)
        val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
        while (storage.isImageRecoveryPending() && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(20)
        }
        assertFalse(storage.isImageRecoveryPending())
    }
}
