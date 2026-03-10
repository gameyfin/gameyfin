package org.gameyfin.app.media

import io.mockk.*
import org.apache.tika.io.TikaInputStream
import org.gameyfin.app.core.events.GameDeletedEvent
import org.gameyfin.app.core.events.GameUpdatedEvent
import org.gameyfin.app.core.events.UserDeletedEvent
import org.gameyfin.app.core.events.UserUpdatedEvent
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.games.repositories.ImageRepository
import org.gameyfin.app.users.entities.User
import org.gameyfin.app.users.persistence.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImageServiceTest {

    private lateinit var imageRepository: ImageRepository
    private lateinit var fileStorageService: FileStorageService
    private lateinit var gameRepository: GameRepository
    private lateinit var userRepository: UserRepository
    private lateinit var imageService: ImageService

    @BeforeEach
    fun setup() {
        imageRepository = mockk()
        fileStorageService = mockk()
        gameRepository = mockk()
        userRepository = mockk()
        imageService = ImageService(imageRepository, fileStorageService, gameRepository, userRepository)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `createOrGet should save image when no original URL exists`() {
        val image = Image(type = ImageType.COVER)
        val savedImage = Image(id = 1L, type = ImageType.COVER)

        every { imageRepository.save(image) } returns savedImage

        val result = imageService.createOrGet(image)

        assertEquals(savedImage, result)
        verify(exactly = 1) { imageRepository.save(image) }
        verify(exactly = 0) { imageRepository.findAllByOriginalUrl(any()) }
    }

    @Test
    fun `createOrGet should save image when original URL is blank`() {
        val image = Image(originalUrl = "", type = ImageType.COVER)
        val savedImage = Image(id = 1L, originalUrl = "", type = ImageType.COVER)

        every { imageRepository.save(image) } returns savedImage

        val result = imageService.createOrGet(image)

        assertEquals(savedImage, result)
        verify(exactly = 1) { imageRepository.save(image) }
        verify(exactly = 0) { imageRepository.findAllByOriginalUrl(any()) }
    }

    @Test
    fun `createOrGet should return existing image when URL already exists`() {
        val url = "https://example.com/image.jpg"
        val image = Image(originalUrl = url, type = ImageType.COVER)
        val existingImage = Image(id = 1L, originalUrl = url, type = ImageType.COVER)

        every { imageRepository.findAllByOriginalUrl(url) } returns listOf(existingImage)

        val result = imageService.createOrGet(image)

        assertEquals(existingImage, result)
        verify(exactly = 1) { imageRepository.findAllByOriginalUrl(url) }
        verify(exactly = 0) { imageRepository.save(any()) }
    }

    @Test
    fun `createOrGet should save image when URL does not exist`() {
        val url = "https://example.com/image.jpg"
        val image = Image(originalUrl = url, type = ImageType.COVER)
        val savedImage = Image(id = 1L, originalUrl = url, type = ImageType.COVER)

        every { imageRepository.findAllByOriginalUrl(url) } returns emptyList()
        every { imageRepository.save(any()) } returns savedImage

        val result = imageService.createOrGet(image)

        assertEquals(savedImage, result)
        verify(exactly = 1) { imageRepository.findAllByOriginalUrl(url) }
        verify(exactly = 1) { imageRepository.save(any()) }
    }

    @Test
    fun `createOrGet should handle concurrent insertion with DataIntegrityViolationException`() {
        val url = "https://example.com/image.jpg"
        val image = Image(originalUrl = url, type = ImageType.COVER)
        val existingImage = Image(id = 1L, originalUrl = url, type = ImageType.COVER)

        every { imageRepository.findAllByOriginalUrl(url) } returnsMany listOf(
            emptyList(),
            listOf(existingImage)
        )
        every { imageRepository.save(any()) } throws DataIntegrityViolationException("Duplicate key")

        val result = imageService.createOrGet(image)

        assertEquals(existingImage, result)
        verify(exactly = 2) { imageRepository.findAllByOriginalUrl(url) }
        verify(exactly = 1) { imageRepository.save(any()) }
    }

    @Test
    fun `createOrGet should throw exception when concurrent insertion fails to find image`() {
        val url = "https://example.com/image.jpg"
        val image = Image(originalUrl = url, type = ImageType.COVER)
        val exception = DataIntegrityViolationException("Duplicate key")

        every { imageRepository.findAllByOriginalUrl(url) } returnsMany listOf(
            emptyList(),
            emptyList()
        )
        every { imageRepository.save(any()) } throws exception

        assertThrows(DataIntegrityViolationException::class.java) {
            imageService.createOrGet(image)
        }

        verify(exactly = 2) { imageRepository.findAllByOriginalUrl(url) }
        verify(exactly = 1) { imageRepository.save(any()) }
    }

    @Test
    fun `createOrGet should return first image when multiple exist with same URL`() {
        val url = "https://example.com/image.jpg"
        val image = Image(originalUrl = url, type = ImageType.COVER)
        val existingImage1 = Image(id = 1L, originalUrl = url, type = ImageType.COVER)
        val existingImage2 = Image(id = 2L, originalUrl = url, type = ImageType.COVER)

        every { imageRepository.findAllByOriginalUrl(url) } returns listOf(existingImage1, existingImage2)

        val result = imageService.createOrGet(image)

        assertEquals(existingImage1, result)
        verify(exactly = 1) { imageRepository.findAllByOriginalUrl(url) }
    }

    @Test
    fun `downloadIfNew should throw exception when image has no original URL`() {
        val image = Image(type = ImageType.COVER)

        assertThrows(IllegalArgumentException::class.java) {
            imageService.downloadIfNew(image)
        }
    }

    @Test
    fun `downloadIfNew should associate existing content when image has valid content`() {
        val url = "https://example.com/image.jpg"
        val image = Image(id = 2L, originalUrl = url, type = ImageType.COVER)
        val existingImage = Image(
            id = 1L,
            originalUrl = url,
            type = ImageType.COVER,
            contentId = "existing-content-id",
            contentLength = 1024L,
            mimeType = "image/jpeg"
        )

        every { imageRepository.findAllByOriginalUrl(url) } returns listOf(existingImage)
        every { fileStorageService.fileExists("existing-content-id") } returns true

        imageService.downloadIfNew(image)

        assertEquals("existing-content-id", image.contentId)
        assertEquals(1024L, image.contentLength)
        assertEquals("image/jpeg", image.mimeType)
        verify(exactly = 0) { fileStorageService.saveFile(any()) }
    }

    @Test
    fun `downloadIfNew should not associate content when existing image has null contentId`() {
        val url = "https://example.com/image.jpg"
        val image = Image(id = 2L, originalUrl = url, type = ImageType.COVER)
        val existingImage = Image(
            id = 1L,
            originalUrl = url,
            type = ImageType.COVER,
            contentId = null,
            contentLength = 0L,
            mimeType = "image/jpeg"
        )

        mockkStatic(TikaInputStream::class)
        val testData = "test image data".toByteArray()
        every { TikaInputStream.get(any<org.apache.tika.io.InputStreamFactory>()) } answers {
            TikaInputStream.get(ByteArrayInputStream(testData))
        }

        every { imageRepository.findAllByOriginalUrl(url) } returns listOf(existingImage)
        every { fileStorageService.fileExists(null) } returns false
        every { fileStorageService.saveFile(any()) } returns "new-content-id"
        every { imageRepository.save(image) } returns image

        imageService.downloadIfNew(image)

        verify(exactly = 1) { fileStorageService.saveFile(any()) }
        verify(exactly = 1) { imageRepository.save(image) }
        unmockkStatic(TikaInputStream::class)
    }

    @Test
    fun `downloadIfNew should download when existing image has zero content length`() {
        val url = "https://example.com/image.jpg"
        val image = Image(id = 2L, originalUrl = url, type = ImageType.COVER)
        val existingImage = Image(
            id = 1L,
            originalUrl = url,
            type = ImageType.COVER,
            contentId = "existing-content-id",
            contentLength = 0L,
            mimeType = "image/jpeg"
        )

        mockkStatic(TikaInputStream::class)
        val testData = "test image data".toByteArray()
        every { TikaInputStream.get(any<org.apache.tika.io.InputStreamFactory>()) } answers {
            TikaInputStream.get(ByteArrayInputStream(testData))
        }

        every { imageRepository.findAllByOriginalUrl(url) } returns listOf(existingImage)
        every { fileStorageService.fileExists("existing-content-id") } returns true
        every { fileStorageService.saveFile(any()) } returns "new-content-id"
        every { imageRepository.save(image) } returns image

        imageService.downloadIfNew(image)

        verify(exactly = 1) { fileStorageService.saveFile(any()) }
        verify(exactly = 1) { imageRepository.save(image) }
        unmockkStatic(TikaInputStream::class)
    }

    @Test
    fun `downloadIfNew should download when no existing image found`() {
        val url = "https://example.com/image.jpg"
        val image = Image(id = 1L, originalUrl = url, type = ImageType.COVER)

        mockkStatic(TikaInputStream::class)
        val testData = "test image data".toByteArray()
        every { TikaInputStream.get(any<org.apache.tika.io.InputStreamFactory>()) } answers {
            TikaInputStream.get(ByteArrayInputStream(testData))
        }

        every { imageRepository.findAllByOriginalUrl(url) } returns emptyList()
        every { fileStorageService.saveFile(any()) } returns "new-content-id"
        every { imageRepository.save(image) } returns image

        imageService.downloadIfNew(image)

        verify(exactly = 1) { fileStorageService.saveFile(any()) }
        verify(exactly = 1) { imageRepository.save(image) }
        unmockkStatic(TikaInputStream::class)
    }

    @Test
    fun `downloadIfNew should ignore DataIntegrityViolationException when saving`() {
        val url = "https://example.com/image.jpg"
        val image = Image(id = 1L, originalUrl = url, type = ImageType.COVER)

        mockkStatic(TikaInputStream::class)
        val testData = "test image data".toByteArray()
        every { TikaInputStream.get(any<org.apache.tika.io.InputStreamFactory>()) } answers {
            TikaInputStream.get(ByteArrayInputStream(testData))
        }

        every { imageRepository.findAllByOriginalUrl(url) } returns emptyList()
        every { fileStorageService.saveFile(any()) } returns "new-content-id"
        every { imageRepository.save(image) } throws DataIntegrityViolationException("Duplicate")

        imageService.downloadIfNew(image)

        verify(exactly = 1) { fileStorageService.saveFile(any()) }
        verify(exactly = 1) { imageRepository.save(image) }
        unmockkStatic(TikaInputStream::class)
    }

    @Test
    fun `createFromInputStream should create and save image with content`() {
        val inputStream = ByteArrayInputStream("image data".toByteArray())
        val savedImage = Image(id = 1L, type = ImageType.AVATAR, mimeType = "image/png")

        every { imageRepository.save(any<Image>()) } returns savedImage
        every { fileStorageService.saveFile(any()) } returns "content-id"

        val result = imageService.createFromInputStream(ImageType.AVATAR, inputStream, "image/png")

        assertNotNull(result)
        verify(exactly = 1) { imageRepository.save(any<Image>()) }
        verify(exactly = 1) { fileStorageService.saveFile(any()) }
    }

    @Test
    fun `getImage should return image when it exists`() {
        val image = Image(id = 1L, type = ImageType.COVER)

        every { imageRepository.findByIdOrNull(1L) } returns image

        val result = imageService.getImage(1L)

        assertEquals(image, result)
        verify(exactly = 1) { imageRepository.findByIdOrNull(1L) }
    }

    @Test
    fun `getImage should return null when image does not exist`() {
        every { imageRepository.findByIdOrNull(999L) } returns null

        val result = imageService.getImage(999L)

        assertNull(result)
        verify(exactly = 1) { imageRepository.findByIdOrNull(999L) }
    }

    @Test
    fun `getFileContent should return input stream from content store`() {
        val image = Image(id = 1L, type = ImageType.COVER, contentId = "content-id")
        val inputStream = ByteArrayInputStream("image data".toByteArray())

        every { fileStorageService.getFile("content-id") } returns inputStream

        val result = imageService.getFileContent(image)

        assertEquals(inputStream, result)
        verify(exactly = 1) { fileStorageService.getFile("content-id") }
    }

    @Test
    fun `getFileContent should return null when content store returns null`() {
        val image = Image(id = 1L, type = ImageType.COVER, contentId = "content-id")

        every { fileStorageService.getFile("content-id") } returns null

        val result = imageService.getFileContent(image)

        assertNull(result)
        verify(exactly = 1) { fileStorageService.getFile("content-id") }
    }

    @Test
    fun `deleteImageIfUnused should delete image when not in use`() {
        val image = Image(id = 1L, type = ImageType.COVER)

        every { gameRepository.existsByImage(1L) } returns false
        every { userRepository.existsByAvatar(1L) } returns false
        every { imageRepository.delete(image) } just Runs
        every { fileStorageService.deleteFile(any()) } just Runs

        imageService.deleteImageIfUnused(image)

        verify(exactly = 1) { gameRepository.existsByImage(1L) }
        verify(exactly = 1) { userRepository.existsByAvatar(1L) }
        verify(exactly = 1) { imageRepository.delete(image) }
        verify(exactly = 1) { fileStorageService.deleteFile(any()) }
    }

    @Test
    fun `deleteImageIfUnused should not delete image when used by game`() {
        val image = Image(id = 1L, type = ImageType.COVER)

        every { gameRepository.existsByImage(1L) } returns true

        imageService.deleteImageIfUnused(image)

        verify(exactly = 1) { gameRepository.existsByImage(1L) }
        verify(exactly = 0) { userRepository.existsByAvatar(any()) }
        verify(exactly = 0) { imageRepository.delete(any()) }
        verify(exactly = 0) { fileStorageService.deleteFile(any()) }
    }

    @Test
    fun `deleteImageIfUnused should not delete image when used by user`() {
        val image = Image(id = 1L, type = ImageType.AVATAR)

        every { gameRepository.existsByImage(1L) } returns false
        every { userRepository.existsByAvatar(1L) } returns true

        imageService.deleteImageIfUnused(image)

        verify(exactly = 1) { gameRepository.existsByImage(1L) }
        verify(exactly = 1) { userRepository.existsByAvatar(1L) }
        verify(exactly = 0) { imageRepository.delete(any()) }
        verify(exactly = 0) { fileStorageService.deleteFile(any()) }
    }

    @Test
    fun `deleteImageIfUnused should do nothing when image has no ID`() {
        val image = Image(type = ImageType.COVER)

        imageService.deleteImageIfUnused(image)

        verify(exactly = 0) { gameRepository.existsByImage(any()) }
        verify(exactly = 0) { userRepository.existsByAvatar(any()) }
        verify(exactly = 0) { imageRepository.delete(any()) }
        verify(exactly = 0) { fileStorageService.deleteFile(any()) }
    }

    @Test
    fun `updateFileContent should update content and mime type`() {
        val image = Image(id = 1L, type = ImageType.AVATAR, mimeType = "image/png")
        val inputStream = ByteArrayInputStream("new image data".toByteArray())

        every { imageRepository.save(image) } returns image
        every { fileStorageService.deleteFile(any()) } just Runs
        every { fileStorageService.saveFile(any()) } returns "new-content-id"

        imageService.updateFileContent(image, inputStream, "image/jpeg")

        assertEquals("image/jpeg", image.mimeType)
        verify(exactly = 1) { imageRepository.save(image) }
        verify(exactly = 1) { fileStorageService.saveFile(any()) }
    }

    @Test
    fun `updateFileContent should update content without changing mime type when null`() {
        val image = Image(id = 1L, type = ImageType.AVATAR, mimeType = "image/png")
        val inputStream = ByteArrayInputStream("new image data".toByteArray())

        every { imageRepository.save(image) } returns image
        every { fileStorageService.deleteFile(any()) } just Runs
        every { fileStorageService.saveFile(any()) } returns "new-content-id"

        imageService.updateFileContent(image, inputStream)

        assertEquals("image/png", image.mimeType)
        verify(exactly = 1) { imageRepository.save(image) }
        verify(exactly = 1) { fileStorageService.saveFile(any()) }
    }

    @Test
    fun `onGameDeleted should delete unused images`() {
        val coverImage = Image(id = 1L, type = ImageType.COVER)
        val headerImage = Image(id = 2L, type = ImageType.HEADER)
        val screenshot = Image(id = 3L, type = ImageType.SCREENSHOT)
        val game = mockk<Game>(relaxed = true) {
            every { this@mockk.coverImage } returns coverImage
            every { this@mockk.headerImage } returns headerImage
            every { this@mockk.images } returns mutableListOf(screenshot)
        }
        val event = GameDeletedEvent(this, game)

        every { gameRepository.existsByImage(1L) } returns false
        every { userRepository.existsByAvatar(1L) } returns false
        every { gameRepository.existsByImage(2L) } returns false
        every { userRepository.existsByAvatar(2L) } returns false
        every { gameRepository.existsByImage(3L) } returns false
        every { userRepository.existsByAvatar(3L) } returns false
        every { imageRepository.delete(any()) } just Runs
        every { fileStorageService.deleteFile(any()) } just Runs

        imageService.onGameDeleted(event)

        verify(exactly = 1) { imageRepository.delete(coverImage) }
        verify(exactly = 1) { imageRepository.delete(headerImage) }
        verify(exactly = 1) { imageRepository.delete(screenshot) }
    }

    @Test
    fun `onGameDeleted should handle null cover and header images`() {
        val screenshot = Image(id = 1L, type = ImageType.SCREENSHOT)
        val game = mockk<Game>(relaxed = true) {
            every { this@mockk.coverImage } returns null
            every { this@mockk.headerImage } returns null
            every { this@mockk.images } returns mutableListOf(screenshot)
        }
        val event = GameDeletedEvent(this, game)

        every { gameRepository.existsByImage(1L) } returns false
        every { userRepository.existsByAvatar(1L) } returns false
        every { imageRepository.delete(screenshot) } just Runs
        every { fileStorageService.deleteFile(any()) } just Runs

        imageService.onGameDeleted(event)

        verify(exactly = 1) { imageRepository.delete(screenshot) }
        verify(exactly = 1) { fileStorageService.deleteFile(any()) }
    }

    @Test
    fun `onGameUpdated should delete images no longer in use`() {
        val oldCover = Image(id = 1L, type = ImageType.COVER)
        val newCover = Image(id = 2L, type = ImageType.COVER)
        val oldScreenshot = Image(id = 3L, type = ImageType.SCREENSHOT)
        val newScreenshot = Image(id = 4L, type = ImageType.SCREENSHOT)

        val previousGame = mockk<Game>(relaxed = true) {
            every { this@mockk.coverImage } returns oldCover
            every { this@mockk.headerImage } returns null
            every { this@mockk.images } returns mutableListOf(oldScreenshot)
        }
        val currentGame = mockk<Game>(relaxed = true) {
            every { this@mockk.coverImage } returns newCover
            every { this@mockk.headerImage } returns null
            every { this@mockk.images } returns mutableListOf(newScreenshot)
        }
        val event = GameUpdatedEvent(this, previousGame, currentGame)

        every { gameRepository.existsByImage(1L) } returns false
        every { userRepository.existsByAvatar(1L) } returns false
        every { gameRepository.existsByImage(3L) } returns false
        every { userRepository.existsByAvatar(3L) } returns false
        every { imageRepository.delete(any()) } just Runs
        every { fileStorageService.deleteFile(any()) } just Runs

        imageService.onGameUpdated(event)

        verify(exactly = 1) { imageRepository.delete(oldCover) }
        verify(exactly = 1) { imageRepository.delete(oldScreenshot) }
        verify(exactly = 0) { imageRepository.delete(newCover) }
        verify(exactly = 0) { imageRepository.delete(newScreenshot) }
    }

    @Test
    fun `onGameUpdated should not delete images still in use`() {
        val cover = Image(id = 1L, type = ImageType.COVER)
        val screenshot = Image(id = 2L, type = ImageType.SCREENSHOT)

        val previousGame = mockk<Game>(relaxed = true) {
            every { this@mockk.coverImage } returns cover
            every { this@mockk.headerImage } returns null
            every { this@mockk.images } returns mutableListOf(screenshot)
        }
        val currentGame = mockk<Game>(relaxed = true) {
            every { this@mockk.coverImage } returns cover
            every { this@mockk.headerImage } returns null
            every { this@mockk.images } returns mutableListOf(screenshot)
        }
        val event = GameUpdatedEvent(this, previousGame, currentGame)

        imageService.onGameUpdated(event)

        verify(exactly = 0) { imageRepository.delete(any()) }
        verify(exactly = 0) { fileStorageService.deleteFile(any()) }
    }

    @Test
    fun `onAccountDeleted should delete avatar when present`() {
        val avatar = Image(id = 1L, type = ImageType.AVATAR)
        val user = mockk<User>(relaxed = true) {
            every { this@mockk.avatar } returns avatar
        }
        val event = UserDeletedEvent(this, user, "http://localhost")

        every { gameRepository.existsByImage(1L) } returns false
        every { userRepository.existsByAvatar(1L) } returns false
        every { imageRepository.delete(avatar) } just Runs
        every { fileStorageService.deleteFile(any()) } just Runs

        imageService.onAccountDeleted(event)

        verify(exactly = 1) { imageRepository.delete(avatar) }
        verify(exactly = 1) { fileStorageService.deleteFile(any()) }
    }

    @Test
    fun `onAccountDeleted should do nothing when avatar is null`() {
        val user = mockk<User>(relaxed = true) {
            every { this@mockk.avatar } returns null
        }
        val event = UserDeletedEvent(this, user, "http://localhost")

        imageService.onAccountDeleted(event)

        verify(exactly = 0) { imageRepository.delete(any()) }
        verify(exactly = 0) { fileStorageService.deleteFile(any()) }
    }

    @Test
    fun `onUserUpdated should delete old avatar when changed`() {
        val oldAvatar = Image(id = 1L, type = ImageType.AVATAR)
        val newAvatar = Image(id = 2L, type = ImageType.AVATAR)
        val previousUser = mockk<User>(relaxed = true) {
            every { this@mockk.avatar } returns oldAvatar
        }
        val currentUser = mockk<User>(relaxed = true) {
            every { this@mockk.avatar } returns newAvatar
        }
        val event = UserUpdatedEvent(this, previousUser, currentUser)

        every { gameRepository.existsByImage(1L) } returns false
        every { userRepository.existsByAvatar(1L) } returns false
        every { imageRepository.delete(oldAvatar) } just Runs
        every { fileStorageService.deleteFile(any()) } just Runs

        imageService.onUserUpdated(event)

        verify(exactly = 1) { imageRepository.delete(oldAvatar) }
        verify(exactly = 1) { fileStorageService.deleteFile(any()) }
    }

    @Test
    fun `onUserUpdated should not delete avatar when unchanged`() {
        val avatar = Image(id = 1L, type = ImageType.AVATAR)
        val previousUser = mockk<User>(relaxed = true) {
            every { this@mockk.avatar } returns avatar
        }
        val currentUser = mockk<User>(relaxed = true) {
            every { this@mockk.avatar } returns avatar
        }
        val event = UserUpdatedEvent(this, previousUser, currentUser)

        imageService.onUserUpdated(event)

        verify(exactly = 0) { imageRepository.delete(any()) }
        verify(exactly = 0) { fileStorageService.deleteFile(any()) }
    }

    @Test
    fun `onUserUpdated should do nothing when previous avatar is null`() {
        val newAvatar = Image(id = 1L, type = ImageType.AVATAR)
        val previousUser = mockk<User>(relaxed = true) {
            every { this@mockk.avatar } returns null
        }
        val currentUser = mockk<User>(relaxed = true) {
            every { this@mockk.avatar } returns newAvatar
        }
        val event = UserUpdatedEvent(this, previousUser, currentUser)

        imageService.onUserUpdated(event)

        verify(exactly = 0) { imageRepository.delete(any()) }
        verify(exactly = 0) { fileStorageService.deleteFile(any()) }
    }

    @Test
    fun `onUserUpdated should delete avatar when removed`() {
        val oldAvatar = Image(id = 1L, type = ImageType.AVATAR)
        val previousUser = mockk<User>(relaxed = true) {
            every { this@mockk.avatar } returns oldAvatar
        }
        val currentUser = mockk<User>(relaxed = true) {
            every { this@mockk.avatar } returns null
        }
        val event = UserUpdatedEvent(this, previousUser, currentUser)

        every { gameRepository.existsByImage(1L) } returns false
        every { userRepository.existsByAvatar(1L) } returns false
        every { imageRepository.delete(oldAvatar) } just Runs
        every { fileStorageService.deleteFile(any()) } just Runs

        imageService.onUserUpdated(event)

        verify(exactly = 1) { imageRepository.delete(oldAvatar) }
        verify(exactly = 1) { fileStorageService.deleteFile(any()) }
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    /**
     * Creates a minimal in-memory PNG of the given dimensions filled with a solid colour.
     * Returns the bytes as an [InputStream] that can be fed directly to [ImageIO.read].
     */
    private fun createPngStream(width: Int, height: Int, color: Color = Color.BLUE): ByteArrayInputStream {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.color = color
        g.fillRect(0, 0, width, height)
        g.dispose()
        val out = ByteArrayOutputStream()
        ImageIO.write(img, "png", out)
        return ByteArrayInputStream(out.toByteArray())
    }

    // ---------------------------------------------------------------------------
    // scaleImageForBlurhash – companion object helper (public, directly testable)
    // ---------------------------------------------------------------------------

    @Nested
    inner class ScaleImageForBlurhash {

        @Test
        fun `returns original image when width is already within maxWidth`() {
            val small = BufferedImage(80, 60, BufferedImage.TYPE_INT_RGB)
            val result = ImageService.scaleImageForBlurhash(small, maxWidth = 100)
            assertNotNull(result)
            assertTrue(result === small, "Expected the same instance to be returned for small images")
        }

        @Test
        fun `returns original image when width exactly equals maxWidth`() {
            val exact = BufferedImage(100, 75, BufferedImage.TYPE_INT_RGB)
            val result = ImageService.scaleImageForBlurhash(exact, maxWidth = 100)
            assertTrue(result === exact)
        }

        @Test
        fun `scales down a landscape image to maxWidth maintaining aspect ratio`() {
            val landscape = BufferedImage(400, 200, BufferedImage.TYPE_INT_RGB)
            val result = ImageService.scaleImageForBlurhash(landscape, maxWidth = 100)
            assertEquals(100, result.width)
            assertEquals(50, result.height)   // 200 * (100/400) = 50
        }

        @Test
        fun `scales down a portrait image preserving aspect ratio`() {
            val portrait = BufferedImage(200, 400, BufferedImage.TYPE_INT_RGB)
            val result = ImageService.scaleImageForBlurhash(portrait, maxWidth = 100)
            assertEquals(100, result.width)
            assertEquals(200, result.height)  // 400 * (100/200) = 200
        }

        @Test
        fun `scales down a square image preserving aspect ratio`() {
            val square = BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB)
            val result = ImageService.scaleImageForBlurhash(square, maxWidth = 100)
            assertEquals(100, result.width)
            assertEquals(100, result.height)
        }
    }

    // ---------------------------------------------------------------------------
    // calculateBlurhash – tested indirectly via createFromInputStream
    // The blurhash is stored on the Image entity that processImageContent populates.
    // ---------------------------------------------------------------------------

    @Nested
    inner class CalculateBlurhash {

        @Test
        fun `blurhash is set for a valid landscape PNG`() {
            val pngStream = createPngStream(width = 200, height = 100)
            val savedImage = Image(id = 1L, type = ImageType.COVER, mimeType = "image/png")

            every { fileStorageService.saveFile(any()) } returns "content-id"
            every { imageRepository.save(any<Image>()) } answers { firstArg() }

            val result = imageService.createFromInputStream(ImageType.COVER, pngStream, "image/png")

            assertNotNull(result.blurhash, "Expected a blurhash to be generated for a landscape image")
            assertTrue(result.blurhash!!.isNotBlank())
        }

        @Test
        fun `blurhash is set for a valid portrait PNG`() {
            val pngStream = createPngStream(width = 100, height = 200)

            every { fileStorageService.saveFile(any()) } returns "content-id"
            every { imageRepository.save(any<Image>()) } answers { firstArg() }

            val result = imageService.createFromInputStream(ImageType.COVER, pngStream, "image/png")

            assertNotNull(result.blurhash, "Expected a blurhash to be generated for a portrait image")
            assertTrue(result.blurhash!!.isNotBlank())
        }

        @Test
        fun `blurhash is set for a valid square PNG`() {
            val pngStream = createPngStream(width = 150, height = 150)

            every { fileStorageService.saveFile(any()) } returns "content-id"
            every { imageRepository.save(any<Image>()) } answers { firstArg() }

            val result = imageService.createFromInputStream(ImageType.COVER, pngStream, "image/png")

            assertNotNull(result.blurhash, "Expected a blurhash to be generated for a square image")
            assertTrue(result.blurhash!!.isNotBlank())
        }

        @Test
        fun `blurhash is set for an image already smaller than the scaling threshold`() {
            // 50x50 is below the default maxWidth=100 used in scaleImageForBlurhash
            val pngStream = createPngStream(width = 50, height = 50)

            every { fileStorageService.saveFile(any()) } returns "content-id"
            every { imageRepository.save(any<Image>()) } answers { firstArg() }

            val result = imageService.createFromInputStream(ImageType.COVER, pngStream, "image/png")

            assertNotNull(result.blurhash, "Expected a blurhash even when image is already small")
        }

        @Test
        fun `blurhash is null when input stream does not contain a valid image`() {
            // Feed random non-image bytes; ImageIO.read returns null, so blurhash should be null
            val garbage = ByteArrayInputStream("not-an-image".toByteArray())

            every { fileStorageService.saveFile(any()) } returns "content-id"
            every { imageRepository.save(any<Image>()) } answers { firstArg() }

            val result = imageService.createFromInputStream(ImageType.COVER, garbage, "image/png")

            assertNull(result.blurhash, "Expected null blurhash for non-image input")
        }

        @Test
        fun `blurhash is null for an empty input stream`() {
            val empty = ByteArrayInputStream(ByteArray(0))

            every { fileStorageService.saveFile(any()) } returns "content-id"
            every { imageRepository.save(any<Image>()) } answers { firstArg() }

            val result = imageService.createFromInputStream(ImageType.COVER, empty, "image/png")

            assertNull(result.blurhash, "Expected null blurhash for empty input")
        }
    }
}
