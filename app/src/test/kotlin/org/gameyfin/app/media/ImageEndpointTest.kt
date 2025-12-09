package org.gameyfin.app.media

import io.mockk.*
import org.gameyfin.app.core.plugins.PluginService
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.games.entities.Image
import org.gameyfin.app.games.entities.ImageType
import org.gameyfin.app.users.UserService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ImageEndpointTest {

    private lateinit var imageService: ImageService
    private lateinit var userService: UserService
    private lateinit var pluginService: PluginService
    private lateinit var imageEndpoint: ImageEndpoint

    @BeforeEach
    fun setup() {
        imageService = mockk()
        userService = mockk()
        pluginService = mockk()
        imageEndpoint = ImageEndpoint(imageService, userService, pluginService)

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `getScreenshot should return image content when image exists`() {
        val imageId = 1L
        val image = Image(id = imageId, type = ImageType.SCREENSHOT, mimeType = "image/jpeg", contentLength = 1024L)
        val inputStream = ByteArrayInputStream("image data".toByteArray())

        every { imageService.getImage(imageId) } returns image
        every { imageService.getFileContent(image) } returns inputStream

        val response = imageEndpoint.getScreenshot(imageId)

        assertNotNull(response)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(1024L, response.headers.contentLength)
        assertEquals(MediaType.parseMediaType("image/jpeg"), response.headers.contentType)
        verify(exactly = 1) { imageService.getImage(imageId) }
        verify(exactly = 1) { imageService.getFileContent(image) }
    }

    @Test
    fun `getScreenshot should return not found when image does not exist`() {
        val imageId = 999L

        every { imageService.getImage(imageId) } returns null

        val response = imageEndpoint.getScreenshot(imageId)

        assertNotNull(response)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        verify(exactly = 1) { imageService.getImage(imageId) }
        verify(exactly = 0) { imageService.getFileContent(any()) }
    }

    @Test
    fun `getScreenshot should return not found when file content is null`() {
        val imageId = 1L
        val image = Image(id = imageId, type = ImageType.SCREENSHOT)

        every { imageService.getImage(imageId) } returns image
        every { imageService.getFileContent(image) } returns null

        val response = imageEndpoint.getScreenshot(imageId)

        assertNotNull(response)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        verify(exactly = 1) { imageService.getImage(imageId) }
        verify(exactly = 1) { imageService.getFileContent(image) }
    }

    @Test
    fun `getCover should return image content when image exists`() {
        val imageId = 2L
        val image = Image(id = imageId, type = ImageType.COVER, mimeType = "image/png", contentLength = 2048L)
        val inputStream = ByteArrayInputStream("cover data".toByteArray())

        every { imageService.getImage(imageId) } returns image
        every { imageService.getFileContent(image) } returns inputStream

        val response = imageEndpoint.getCover(imageId)

        assertNotNull(response)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(2048L, response.headers.contentLength)
        assertEquals(MediaType.parseMediaType("image/png"), response.headers.contentType)
        verify(exactly = 1) { imageService.getImage(imageId) }
        verify(exactly = 1) { imageService.getFileContent(image) }
    }

    @Test
    fun `getCover should return not found when image does not exist`() {
        val imageId = 999L

        every { imageService.getImage(imageId) } returns null

        val response = imageEndpoint.getCover(imageId)

        assertNotNull(response)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        verify(exactly = 1) { imageService.getImage(imageId) }
    }

    @Test
    fun `getHeader should return image content when image exists`() {
        val imageId = 3L
        val image = Image(id = imageId, type = ImageType.HEADER, mimeType = "image/webp", contentLength = 4096L)
        val inputStream = ByteArrayInputStream("header data".toByteArray())

        every { imageService.getImage(imageId) } returns image
        every { imageService.getFileContent(image) } returns inputStream

        val response = imageEndpoint.getHeader(imageId)

        assertNotNull(response)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(4096L, response.headers.contentLength)
        assertEquals(MediaType.parseMediaType("image/webp"), response.headers.contentType)
        verify(exactly = 1) { imageService.getImage(imageId) }
        verify(exactly = 1) { imageService.getFileContent(image) }
    }

    @Test
    fun `getHeader should return not found when image does not exist`() {
        val imageId = 999L

        every { imageService.getImage(imageId) } returns null

        val response = imageEndpoint.getHeader(imageId)

        assertNotNull(response)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        verify(exactly = 1) { imageService.getImage(imageId) }
    }

    @Test
    fun `getPluginLogo should return logo when plugin exists`() {
        val pluginId = "test-plugin"
        val logoBytes = "logo data".toByteArray()

        every { pluginService.getLogo(pluginId) } returns logoBytes

        val response = imageEndpoint.getPluginLogo(pluginId)

        assertNotNull(response)
        assertEquals(HttpStatus.OK, response.statusCode)
        verify(exactly = 1) { pluginService.getLogo(pluginId) }
    }

    @Test
    fun `getPluginLogo should return not found when plugin logo is null`() {
        val pluginId = "nonexistent-plugin"

        every { pluginService.getLogo(pluginId) } returns null

        val response = imageEndpoint.getPluginLogo(pluginId)

        assertNotNull(response)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        verify(exactly = 1) { pluginService.getLogo(pluginId) }
    }

    @Test
    fun `getAvatarByUsername should return avatar when user has avatar`() {
        val username = "testuser"
        val avatar = Image(id = 1L, type = ImageType.AVATAR, mimeType = "image/jpeg", contentLength = 512L)
        val inputStream = ByteArrayInputStream("avatar data".toByteArray())

        every { userService.getAvatar(username) } returns avatar
        every { imageService.getImage(1L) } returns avatar
        every { imageService.getFileContent(avatar) } returns inputStream

        val response = imageEndpoint.getAvatarByUsername(username)

        assertNotNull(response)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(512L, response.headers.contentLength)
        assertEquals(MediaType.parseMediaType("image/jpeg"), response.headers.contentType)
        verify(exactly = 1) { userService.getAvatar(username) }
        verify(exactly = 1) { imageService.getImage(1L) }
        verify(exactly = 1) { imageService.getFileContent(avatar) }
    }

    @Test
    fun `getAvatarByUsername should return not found when user has no avatar`() {
        val username = "testuser"

        every { userService.getAvatar(username) } returns null

        val response = imageEndpoint.getAvatarByUsername(username)

        assertNotNull(response)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        verify(exactly = 1) { userService.getAvatar(username) }
        verify(exactly = 0) { imageService.getImage(any()) }
    }

    @Test
    fun `getAvatarByUsername should return not found when avatar has no ID`() {
        val username = "testuser"
        val avatar = Image(type = ImageType.AVATAR)

        every { userService.getAvatar(username) } returns avatar

        val response = imageEndpoint.getAvatarByUsername(username)

        assertNotNull(response)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        verify(exactly = 1) { userService.getAvatar(username) }
        verify(exactly = 0) { imageService.getImage(any()) }
    }

    @Test
    fun `uploadAvatar should create new avatar when user has no avatar`() {
        val auth = mockk<Authentication> {
            every { name } returns "testuser"
        }
        val file = mockk<MultipartFile> {
            every { inputStream } returns ByteArrayInputStream("new avatar".toByteArray())
            every { contentType } returns "image/png"
        }
        val newImage = Image(id = 1L, type = ImageType.AVATAR, mimeType = "image/png")

        every { getCurrentAuth() } returns auth
        every { userService.hasAvatar("testuser") } returns false
        every { imageService.createFromInputStream(ImageType.AVATAR, any(), "image/png") } returns newImage
        every { userService.updateAvatar("testuser", newImage) } just Runs

        imageEndpoint.uploadAvatar(file)

        verify(exactly = 1) { userService.hasAvatar("testuser") }
        verify(exactly = 1) { imageService.createFromInputStream(ImageType.AVATAR, any(), "image/png") }
        verify(exactly = 1) { userService.updateAvatar("testuser", newImage) }
        verify(exactly = 0) { imageService.updateFileContent(any(), any(), any()) }
    }

    @Test
    fun `uploadAvatar should update existing avatar when user has avatar`() {
        val auth = mockk<Authentication> {
            every { name } returns "testuser"
        }
        val file = mockk<MultipartFile> {
            every { inputStream } returns ByteArrayInputStream("updated avatar".toByteArray())
            every { contentType } returns "image/jpeg"
        }
        val existingAvatar = Image(id = 1L, type = ImageType.AVATAR, mimeType = "image/png")
        val updatedImage = Image(id = 1L, type = ImageType.AVATAR, mimeType = "image/jpeg")

        every { getCurrentAuth() } returns auth
        every { userService.hasAvatar("testuser") } returns true
        every { userService.getAvatar("testuser") } returns existingAvatar
        every { imageService.updateFileContent(existingAvatar, any(), "image/jpeg") } returns updatedImage
        every { userService.updateAvatar("testuser", updatedImage) } just Runs

        imageEndpoint.uploadAvatar(file)

        verify(exactly = 1) { userService.hasAvatar("testuser") }
        verify(exactly = 1) { userService.getAvatar("testuser") }
        verify(exactly = 1) { imageService.updateFileContent(existingAvatar, any(), "image/jpeg") }
        verify(exactly = 1) { userService.updateAvatar("testuser", updatedImage) }
        verify(exactly = 0) { imageService.createFromInputStream(any(), any(), any()) }
    }

    @Test
    fun `uploadAvatar should throw exception when no authentication found`() {
        val file = mockk<MultipartFile>()

        every { getCurrentAuth() } returns null

        assertThrows(IllegalStateException::class.java) {
            imageEndpoint.uploadAvatar(file)
        }

        verify(exactly = 0) { userService.hasAvatar(any()) }
        verify(exactly = 0) { imageService.createFromInputStream(any(), any(), any()) }
    }

    @Test
    fun `deleteAvatar should delete avatar for authenticated user`() {
        val auth = mockk<Authentication> {
            every { name } returns "testuser"
        }

        every { getCurrentAuth() } returns auth
        every { userService.deleteAvatar("testuser") } just Runs

        imageEndpoint.deleteAvatar()

        verify(exactly = 1) { userService.deleteAvatar("testuser") }
    }

    @Test
    fun `deleteAvatar should throw exception when no authentication found`() {
        every { getCurrentAuth() } returns null

        assertThrows(IllegalStateException::class.java) {
            imageEndpoint.deleteAvatar()
        }

        verify(exactly = 0) { userService.deleteAvatar(any()) }
    }

    @Test
    fun `deleteAvatarByName should delete avatar for specified user`() {
        val username = "targetuser"

        every { userService.deleteAvatar(username) } just Runs

        imageEndpoint.deleteAvatarByName(username)

        verify(exactly = 1) { userService.deleteAvatar(username) }
    }

    @Test
    fun `getImageContent should handle image without content length`() {
        val imageId = 1L
        val image = Image(id = imageId, type = ImageType.SCREENSHOT, mimeType = "image/jpeg", contentLength = null)
        val inputStream = ByteArrayInputStream("image data".toByteArray())

        every { imageService.getImage(imageId) } returns image
        every { imageService.getFileContent(image) } returns inputStream

        val response = imageEndpoint.getScreenshot(imageId)

        assertNotNull(response)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(MediaType.parseMediaType("image/jpeg"), response.headers.contentType)
        verify(exactly = 1) { imageService.getImage(imageId) }
    }

    @Test
    fun `getImageContent should handle image without mime type`() {
        val imageId = 1L
        val image = Image(id = imageId, type = ImageType.SCREENSHOT, mimeType = null, contentLength = 1024L)
        val inputStream = ByteArrayInputStream("image data".toByteArray())

        every { imageService.getImage(imageId) } returns image
        every { imageService.getFileContent(image) } returns inputStream

        val response = imageEndpoint.getScreenshot(imageId)

        assertNotNull(response)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(1024L, response.headers.contentLength)
        assertNull(response.headers.contentType)
        verify(exactly = 1) { imageService.getImage(imageId) }
    }

    @Test
    fun `getImageContent should handle image without content length and mime type`() {
        val imageId = 1L
        val image = Image(id = imageId, type = ImageType.SCREENSHOT, mimeType = null, contentLength = null)
        val inputStream = ByteArrayInputStream("image data".toByteArray())

        every { imageService.getImage(imageId) } returns image
        every { imageService.getFileContent(image) } returns inputStream

        val response = imageEndpoint.getScreenshot(imageId)

        assertNotNull(response)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNull(response.headers.contentType)
        verify(exactly = 1) { imageService.getImage(imageId) }
    }

    @Test
    fun `uploadAvatar should handle different content types`() {
        val auth = mockk<Authentication> {
            every { name } returns "testuser"
        }
        val file = mockk<MultipartFile> {
            every { inputStream } returns ByteArrayInputStream("webp avatar".toByteArray())
            every { contentType } returns "image/webp"
        }
        val newImage = Image(id = 1L, type = ImageType.AVATAR, mimeType = "image/webp")

        every { getCurrentAuth() } returns auth
        every { userService.hasAvatar("testuser") } returns false
        every { imageService.createFromInputStream(ImageType.AVATAR, any(), "image/webp") } returns newImage
        every { userService.updateAvatar("testuser", newImage) } just Runs

        imageEndpoint.uploadAvatar(file)

        verify(exactly = 1) { imageService.createFromInputStream(ImageType.AVATAR, any(), "image/webp") }
        verify(exactly = 1) { userService.updateAvatar("testuser", newImage) }
    }

    @Test
    fun `getScreenshot should handle large content length`() {
        val imageId = 1L
        val largeSize = Long.MAX_VALUE
        val image = Image(id = imageId, type = ImageType.SCREENSHOT, mimeType = "image/jpeg", contentLength = largeSize)
        val inputStream = ByteArrayInputStream("image data".toByteArray())

        every { imageService.getImage(imageId) } returns image
        every { imageService.getFileContent(image) } returns inputStream

        val response = imageEndpoint.getScreenshot(imageId)

        assertNotNull(response)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(largeSize, response.headers.contentLength)
        verify(exactly = 1) { imageService.getImage(imageId) }
    }

    @Test
    fun `getCover should handle zero content length`() {
        val imageId = 1L
        val image = Image(id = imageId, type = ImageType.COVER, mimeType = "image/png", contentLength = 0L)
        val inputStream = ByteArrayInputStream("".toByteArray())

        every { imageService.getImage(imageId) } returns image
        every { imageService.getFileContent(image) } returns inputStream

        val response = imageEndpoint.getCover(imageId)

        assertNotNull(response)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(0L, response.headers.contentLength)
        verify(exactly = 1) { imageService.getImage(imageId) }
    }

    @Test
    fun `getAvatarByUsername should handle empty username`() {
        val username = ""
        val avatar = Image(id = 1L, type = ImageType.AVATAR, mimeType = "image/jpeg", contentLength = 512L)
        val inputStream = ByteArrayInputStream("avatar data".toByteArray())

        every { userService.getAvatar(username) } returns avatar
        every { imageService.getImage(1L) } returns avatar
        every { imageService.getFileContent(avatar) } returns inputStream

        val response = imageEndpoint.getAvatarByUsername(username)

        assertNotNull(response)
        assertEquals(HttpStatus.OK, response.statusCode)
        verify(exactly = 1) { userService.getAvatar(username) }
    }

    @Test
    fun `getPluginLogo should handle empty plugin id`() {
        val pluginId = ""
        val logoBytes = "logo data".toByteArray()

        every { pluginService.getLogo(pluginId) } returns logoBytes

        val response = imageEndpoint.getPluginLogo(pluginId)

        assertNotNull(response)
        assertEquals(HttpStatus.OK, response.statusCode)
        verify(exactly = 1) { pluginService.getLogo(pluginId) }
    }

    @Test
    fun `deleteAvatarByName should handle empty name`() {
        val username = ""

        every { userService.deleteAvatar(username) } just Runs

        imageEndpoint.deleteAvatarByName(username)

        verify(exactly = 1) { userService.deleteAvatar(username) }
    }

    @Test
    fun `uploadAvatar should preserve original input stream`() {
        val auth = mockk<Authentication> {
            every { name } returns "testuser"
        }
        val avatarData = "new avatar data".toByteArray()
        val inputStream = ByteArrayInputStream(avatarData)
        val file = mockk<MultipartFile> {
            every { this@mockk.inputStream } returns inputStream
            every { contentType } returns "image/png"
        }
        val newImage = Image(id = 1L, type = ImageType.AVATAR, mimeType = "image/png")

        every { getCurrentAuth() } returns auth
        every { userService.hasAvatar("testuser") } returns false
        every { imageService.createFromInputStream(ImageType.AVATAR, inputStream, "image/png") } returns newImage
        every { userService.updateAvatar("testuser", newImage) } just Runs

        imageEndpoint.uploadAvatar(file)

        verify(exactly = 1) { imageService.createFromInputStream(ImageType.AVATAR, inputStream, "image/png") }
    }
}

