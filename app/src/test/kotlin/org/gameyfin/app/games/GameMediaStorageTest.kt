package org.gameyfin.app.games

import com.github.benmanes.caffeine.cache.Caffeine
import io.mockk.*
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.games.repositories.ImageRepository
import org.gameyfin.app.media.FileStorageService
import org.gameyfin.app.media.Image
import org.gameyfin.app.media.ImageService
import org.gameyfin.app.media.ImageType
import org.gameyfin.app.users.persistence.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*

class GameMediaStorageTest {
    @TempDir lateinit var directory: Path

    @AfterEach
    fun cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization()
    }

    @Test
    fun `rollback removes newly stored files and cached images while commit retains them`() {
        val repository = mockk<ImageRepository>()
        val storage = FileStorageService(directory.toString())
        val cache = Caffeine.newBuilder().build<Long, Image>()
        val service = ImageService(repository, storage, mockk(), mockk(), cache)
        every { repository.save(any<Image>()) } answers { firstArg<Image>().apply { id = 1 } }
        val bytes = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()

        for (status in listOf(TransactionSynchronization.STATUS_COMMITTED, TransactionSynchronization.STATUS_ROLLED_BACK)) {
            TransactionSynchronizationManager.initSynchronization()
            val image = service.createFromInputStream(ImageType.SCREENSHOT, bytes.inputStream(), "image/png")
            assertTrue(storage.fileExists(image.contentId))
            assertNotNull(cache.getIfPresent(1))
            TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCompletion(status) }
            TransactionSynchronizationManager.clearSynchronization()
            assertEquals(status == TransactionSynchronization.STATUS_COMMITTED, storage.fileExists(image.contentId))
            assertEquals(status == TransactionSynchronization.STATUS_COMMITTED, cache.getIfPresent(1) != null)
        }
    }

    @Test
    fun `rollback evicts a new remote image even if its download never succeeds`() {
        val repository = mockk<ImageRepository>()
        val cache = Caffeine.newBuilder().build<Long, Image>()
        val service = ImageService(repository, FileStorageService(directory.toString()), mockk(), mockk(), cache)
        every { repository.findAllByOriginalUrl(any()) } returns emptyList()
        every { repository.save(any<Image>()) } answers { firstArg<Image>().apply { id = 1 } }
        TransactionSynchronizationManager.initSynchronization()
        service.createOrGet(Image(originalUrl = "https://example.com/unavailable.png", type = ImageType.COVER))
        assertNotNull(cache.getIfPresent(1))
        TransactionSynchronizationManager.getSynchronizations().forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }
        assertNull(cache.getIfPresent(1))
    }

    @Test
    fun `removing a screenshot retains files used by another game`() {
        val repository = mockk<ImageRepository>()
        val games = mockk<GameRepository>()
        val users = mockk<UserRepository>()
        val storage = FileStorageService(directory.toString())
        val cache = Caffeine.newBuilder().build<Long, Image>()
        val service = ImageService(repository, storage, games, users, cache)
        val image = Image(id = 1, type = ImageType.SCREENSHOT, contentId = storage.saveFile(byteArrayOf(1, 2, 3).inputStream()))
        every { games.existsByImage(1) } returns true
        service.deleteImageIfUnused(image)
        assertTrue(storage.fileExists(image.contentId))
        verify(exactly = 0) { repository.deleteById(any()) }
    }
}
