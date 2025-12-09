package org.gameyfin.app.media

import io.mockk.*
import org.apache.tika.io.TikaInputStream
import org.gameyfin.app.core.events.GameDeletedEvent
import org.gameyfin.app.core.events.GameUpdatedEvent
import org.gameyfin.app.core.events.UserDeletedEvent
import org.gameyfin.app.core.events.UserUpdatedEvent
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.games.repositories.ImageContentStore
import org.gameyfin.app.games.repositories.ImageRepository
import org.gameyfin.app.users.entities.User
import org.gameyfin.app.users.persistence.UserRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.repository.findByIdOrNull
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ImageServiceTest {

    private lateinit var imageRepository: ImageRepository
    private lateinit var imageContentStore: ImageContentStore
    private lateinit var gameRepository: GameRepository
    private lateinit var userRepository: UserRepository
    private lateinit var imageService: ImageService

    @BeforeEach
    fun setup() {
        imageRepository = mockk()
        imageContentStore = mockk()
        gameRepository = mockk()
        userRepository = mockk()
        imageService = ImageService(imageRepository, imageContentStore, gameRepository, userRepository)
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
        val inputStream = ByteArrayInputStream("image data".toByteArray())

        every { imageRepository.findAllByOriginalUrl(url) } returns listOf(existingImage)
        every { imageContentStore.getContent(existingImage) } returns inputStream
        every { imageContentStore.associate(image, "existing-content-id") } just Runs

        imageService.downloadIfNew(image)

        assertEquals("existing-content-id", image.contentId)
        assertEquals(1024L, image.contentLength)
        assertEquals("image/jpeg", image.mimeType)
        verify(exactly = 1) { imageContentStore.associate(image, "existing-content-id") }
        verify(exactly = 0) { imageContentStore.setContent(any(), any<InputStream>()) }
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
        every { imageContentStore.getContent(existingImage) } returns null
        every { imageContentStore.setContent(any<Image>(), any<InputStream>()) } returnsArgument 0
        every { imageRepository.save(image) } returns image

        imageService.downloadIfNew(image)

        verify(exactly = 0) { imageContentStore.associate(any(), any()) }
        verify(exactly = 1) { imageContentStore.setContent(image, any<InputStream>()) }
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
        val inputStream = ByteArrayInputStream("image data".toByteArray())

        mockkStatic(TikaInputStream::class)
        val testData = "test image data".toByteArray()
        every { TikaInputStream.get(any<org.apache.tika.io.InputStreamFactory>()) } answers {
            TikaInputStream.get(ByteArrayInputStream(testData))
        }

        every { imageRepository.findAllByOriginalUrl(url) } returns listOf(existingImage)
        every { imageContentStore.getContent(existingImage) } returns inputStream
        every { imageContentStore.setContent(any<Image>(), any<InputStream>()) } returnsArgument 0
        every { imageRepository.save(image) } returns image

        imageService.downloadIfNew(image)

        verify(exactly = 0) { imageContentStore.associate(any(), any()) }
        verify(exactly = 1) { imageContentStore.setContent(image, any<InputStream>()) }
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
        every { imageContentStore.setContent(any<Image>(), any<InputStream>()) } returnsArgument 0
        every { imageRepository.save(image) } returns image

        imageService.downloadIfNew(image)

        verify(exactly = 1) { imageContentStore.setContent(image, any<InputStream>()) }
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
        every { imageContentStore.setContent(any<Image>(), any<InputStream>()) } returnsArgument 0
        every { imageRepository.save(image) } throws DataIntegrityViolationException("Duplicate")

        imageService.downloadIfNew(image)

        verify(exactly = 1) { imageContentStore.setContent(image, any<InputStream>()) }
        verify(exactly = 1) { imageRepository.save(image) }
        unmockkStatic(TikaInputStream::class)
    }

    @Test
    fun `createFromInputStream should create and save image with content`() {
        val inputStream = ByteArrayInputStream("image data".toByteArray())
        val savedImage = Image(id = 1L, type = ImageType.AVATAR, mimeType = "image/png")

        every { imageRepository.save(any<Image>()) } returns savedImage
        every { imageContentStore.setContent(any<Image>(), any<InputStream>()) } returns savedImage

        val result = imageService.createFromInputStream(ImageType.AVATAR, inputStream, "image/png")

        assertNotNull(result)
        verify(exactly = 1) { imageRepository.save(any<Image>()) }
        verify(exactly = 1) { imageContentStore.setContent(any<Image>(), inputStream) }
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

        every { imageContentStore.getContent(image) } returns inputStream

        val result = imageService.getFileContent(image)

        assertEquals(inputStream, result)
        verify(exactly = 1) { imageContentStore.getContent(image) }
    }

    @Test
    fun `getFileContent should return null when content store returns null`() {
        val image = Image(id = 1L, type = ImageType.COVER, contentId = "content-id")

        every { imageContentStore.getContent(image) } returns null

        val result = imageService.getFileContent(image)

        assertNull(result)
        verify(exactly = 1) { imageContentStore.getContent(image) }
    }

    @Test
    fun `deleteImageIfUnused should delete image when not in use`() {
        val image = Image(id = 1L, type = ImageType.COVER)

        every { gameRepository.existsByImage(1L) } returns false
        every { userRepository.existsByAvatar(1L) } returns false
        every { imageRepository.delete(image) } just Runs
        every { imageContentStore.unsetContent(image) } returnsArgument 0

        imageService.deleteImageIfUnused(image)

        verify(exactly = 1) { gameRepository.existsByImage(1L) }
        verify(exactly = 1) { userRepository.existsByAvatar(1L) }
        verify(exactly = 1) { imageRepository.delete(image) }
        verify(exactly = 1) { imageContentStore.unsetContent(image) }
    }

    @Test
    fun `deleteImageIfUnused should not delete image when used by game`() {
        val image = Image(id = 1L, type = ImageType.COVER)

        every { gameRepository.existsByImage(1L) } returns true

        imageService.deleteImageIfUnused(image)

        verify(exactly = 1) { gameRepository.existsByImage(1L) }
        verify(exactly = 0) { userRepository.existsByAvatar(any()) }
        verify(exactly = 0) { imageRepository.delete(any()) }
        verify(exactly = 0) { imageContentStore.unsetContent(any()) }
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
        verify(exactly = 0) { imageContentStore.unsetContent(any()) }
    }

    @Test
    fun `deleteImageIfUnused should do nothing when image has no ID`() {
        val image = Image(type = ImageType.COVER)

        imageService.deleteImageIfUnused(image)

        verify(exactly = 0) { gameRepository.existsByImage(any()) }
        verify(exactly = 0) { userRepository.existsByAvatar(any()) }
        verify(exactly = 0) { imageRepository.delete(any()) }
        verify(exactly = 0) { imageContentStore.unsetContent(any()) }
    }

    @Test
    fun `updateFileContent should update content and mime type`() {
        val image = Image(id = 1L, type = ImageType.AVATAR, mimeType = "image/png")
        val inputStream = ByteArrayInputStream("new image data".toByteArray())

        every { imageRepository.save(image) } returns image
        every { imageContentStore.setContent(any<Image>(), any<InputStream>()) } returns image

        imageService.updateFileContent(image, inputStream, "image/jpeg")

        assertEquals("image/jpeg", image.mimeType)
        verify(exactly = 1) { imageRepository.save(image) }
        verify(exactly = 1) { imageContentStore.setContent(image, inputStream) }
    }

    @Test
    fun `updateFileContent should update content without changing mime type when null`() {
        val image = Image(id = 1L, type = ImageType.AVATAR, mimeType = "image/png")
        val inputStream = ByteArrayInputStream("new image data".toByteArray())

        every { imageRepository.save(image) } returns image
        every { imageContentStore.setContent(any<Image>(), any<InputStream>()) } returns image

        imageService.updateFileContent(image, inputStream, null)

        assertEquals("image/png", image.mimeType)
        verify(exactly = 1) { imageRepository.save(image) }
        verify(exactly = 1) { imageContentStore.setContent(image, inputStream) }
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
        every { imageContentStore.unsetContent(any()) } returnsArgument 0

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
        every { imageContentStore.unsetContent(screenshot) } returnsArgument 0

        imageService.onGameDeleted(event)

        verify(exactly = 1) { imageRepository.delete(screenshot) }
        verify(exactly = 1) { imageContentStore.unsetContent(screenshot) }
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
        every { imageContentStore.unsetContent(any()) } returnsArgument 0

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
        verify(exactly = 0) { imageContentStore.unsetContent(any()) }
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
        every { imageContentStore.unsetContent(avatar) } returnsArgument 0

        imageService.onAccountDeleted(event)

        verify(exactly = 1) { imageRepository.delete(avatar) }
        verify(exactly = 1) { imageContentStore.unsetContent(avatar) }
    }

    @Test
    fun `onAccountDeleted should do nothing when avatar is null`() {
        val user = mockk<User>(relaxed = true) {
            every { this@mockk.avatar } returns null
        }
        val event = UserDeletedEvent(this, user, "http://localhost")

        imageService.onAccountDeleted(event)

        verify(exactly = 0) { imageRepository.delete(any()) }
        verify(exactly = 0) { imageContentStore.unsetContent(any()) }
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
        every { imageContentStore.unsetContent(oldAvatar) } returnsArgument 0

        imageService.onUserUpdated(event)

        verify(exactly = 1) { imageRepository.delete(oldAvatar) }
        verify(exactly = 1) { imageContentStore.unsetContent(oldAvatar) }
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
        verify(exactly = 0) { imageContentStore.unsetContent(any()) }
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
        verify(exactly = 0) { imageContentStore.unsetContent(any()) }
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
        every { imageContentStore.unsetContent(oldAvatar) } returnsArgument 0

        imageService.onUserUpdated(event)

        verify(exactly = 1) { imageRepository.delete(oldAvatar) }
        verify(exactly = 1) { imageContentStore.unsetContent(oldAvatar) }
    }
}
