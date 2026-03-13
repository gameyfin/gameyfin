package org.gameyfin.app.media

import io.mockk.*
import jakarta.servlet.http.HttpServletRequest
import org.gameyfin.app.core.plugins.PluginService
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.users.UserService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.web.multipart.MultipartFile
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ImageEndpointTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var imageService: ImageService
    private lateinit var userService: UserService
    private lateinit var pluginService: PluginService
    private lateinit var imageEndpoint: ImageEndpoint
    private lateinit var mockRequest: HttpServletRequest

    @BeforeEach
    fun setup() {
        imageService = mockk()
        userService = mockk()
        pluginService = mockk()
        imageEndpoint = ImageEndpoint(imageService, userService, pluginService)
        mockRequest = mockk {
            every { getHeader(HttpHeaders.IF_NONE_MATCH) } returns null
        }

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    private fun createTempFile(content: String = "image data"): Path {
        val file = Files.createTempFile(tempDir, "test-img-", ".tmp")
        Files.write(file, content.toByteArray())
        return file
    }

    @Test
    fun `getScreenshot should return image content when image exists`() {
        val imageId = 1L
        val image = Image(id = imageId, type = ImageType.SCREENSHOT, mimeType = "image/jpeg", contentLength = 1024L, contentId = "abc")
        val filePath = createTempFile()

        every { imageService.getImage(imageId) } returns image
        every { imageService.getFilePath(image) } returns filePath

        val response = imageEndpoint.getScreenshot(imageId, mockRequest)

        assertNotNull(response)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(1024L, response.headers.contentLength)
        assertEquals(MediaType.parseMediaType("image/jpeg"), response.headers.contentType)
        verify(exactly = 1) { imageService.getImage(imageId) }
        verify(exactly = 1) { imageService.getFilePath(image) }
    }

    @Test
    fun `getScreenshot should return not found when image does not exist`() {
        val imageId = 999L

        every { imageService.getImage(imageId) } returns null

        val response = imageEndpoint.getScreenshot(imageId, mockRequest)

        assertNotNull(response)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        verify(exactly = 1) { imageService.getImage(imageId) }
        verify(exactly = 0) { imageService.getFilePath(any()) }
    }

    @Test
    fun `getScreenshot should return not found when file path is null`() {
        val imageId = 1L
        val image = Image(id = imageId, type = ImageType.SCREENSHOT)

        every { imageService.getImage(imageId) } returns image
        every { imageService.getFilePath(image) } returns null

        val response = imageEndpoint.getScreenshot(imageId, mockRequest)

        assertNotNull(response)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        verify(exactly = 1) { imageService.getImage(imageId) }
        verify(exactly = 1) { imageService.getFilePath(image) }
    }

    @Test
    fun `getCover should return image content when image exists`() {
        val imageId = 2L
        val image = Image(id = imageId, type = ImageType.COVER, mimeType = "image/png", contentLength = 2048L, contentId = "def")
        val filePath = createTempFile("cover data")

        every { imageService.getImage(imageId) } returns image
        every { imageService.getFilePath(image) } returns filePath

        val response = imageEndpoint.getCover(imageId, mockRequest)

        assertNotNull(response)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(2048L, response.headers.contentLength)
        assertEquals(MediaType.parseMediaType("image/png"), response.headers.contentType)
        verify(exactly = 1) { imageService.getImage(imageId) }
        verify(exactly = 1) { imageService.getFilePath(image) }
    }

    @Test
    fun `getCover should return not found when image does not exist`() {
        val imageId = 999L

        every { imageService.getImage(imageId) } returns null

        val response = imageEndpoint.getCover(imageId, mockRequest)

        assertNotNull(response)
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        verify(exactly = 1) { imageService.getImage(imageId) }
    }

    @Test
    fun `getHeader should return image content when image exists`() {
        val imageId = 3L
        val image = Image(id = imageId, type = ImageType.HEADER, mimeType = "image/webp", contentLength = 4096L, contentId = "ghi")
        val filePath = createTempFile("header data")

        every { imageService.getImage(imageId) } returns image
        every { imageService.getFilePath(image) } returns filePath

        val response = imageEndpoint.getHeader(imageId, mockRequest)

        assertNotNull(response)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(4096L, response.headers.contentLength)
        assertEquals(MediaType.parseMediaType("image/webp"), response.headers.contentType)
        verify(exactly = 1) { imageService.getImage(imageId) }
        verify(exactly = 1) { imageService.getFilePath(image) }
    }

    @Test
    fun `getHeader should return not found when image does not exist`() {
        val imageId = 999L

        every { imageService.getImage(imageId) } returns null

        val response = imageEndpoint.getHeader(imageId, mockRequest)

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
        val avatar = Image(id = 1L, type = ImageType.AVATAR, mimeType = "image/jpeg", contentLength = 512L, contentId = "avatar-id")
        val filePath = createTempFile("avatar data")

        every { userService.getAvatar(username) } returns avatar
        every { imageService.getImage(1L) } returns avatar
        every { imageService.getFilePath(avatar) } returns filePath

        val response = imageEndpoint.getAvatarByUsername(username, mockRequest)

        assertNotNull(response)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(512L, response.headers.contentLength)
        assertEquals(MediaType.parseMediaType("image/jpeg"), response.headers.contentType)
        verify(exactly = 1) { userService.getAvatar(username) }
        verify(exactly = 1) { imageService.getImage(1L) }
        verify(exactly = 1) { imageService.getFilePath(avatar) }
    }

    @Test
    fun `getAvatarByUsername should return not found when user has no avatar`() {
        val username = "testuser"

        every { userService.getAvatar(username) } returns null

        val response = imageEndpoint.getAvatarByUsername(username, mockRequest)

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

        val response = imageEndpoint.getAvatarByUsername(username, mockRequest)

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
        val image = Image(id = imageId, type = ImageType.SCREENSHOT, mimeType = "image/jpeg", contentLength = null, contentId = "abc")
        val filePath = createTempFile()

        every { imageService.getImage(imageId) } returns image
        every { imageService.getFilePath(image) } returns filePath

        val response = imageEndpoint.getScreenshot(imageId, mockRequest)

        assertNotNull(response)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(MediaType.parseMediaType("image/jpeg"), response.headers.contentType)
        verify(exactly = 1) { imageService.getImage(imageId) }
    }

    @Test
    fun `getImageContent should handle image without mime type`() {
        val imageId = 1L
        val image = Image(id = imageId, type = ImageType.SCREENSHOT, mimeType = null, contentLength = 1024L, contentId = "abc")
        val filePath = createTempFile()

        every { imageService.getImage(imageId) } returns image
        every { imageService.getFilePath(image) } returns filePath

        val response = imageEndpoint.getScreenshot(imageId, mockRequest)

        assertNotNull(response)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(1024L, response.headers.contentLength)
        assertNull(response.headers.contentType)
        verify(exactly = 1) { imageService.getImage(imageId) }
    }

    @Test
    fun `getImageContent should handle image without content length and mime type`() {
        val imageId = 1L
        val image = Image(id = imageId, type = ImageType.SCREENSHOT, mimeType = null, contentLength = null, contentId = "abc")
        val filePath = createTempFile()

        every { imageService.getImage(imageId) } returns image
        every { imageService.getFilePath(image) } returns filePath

        val response = imageEndpoint.getScreenshot(imageId, mockRequest)

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
        val image = Image(id = imageId, type = ImageType.SCREENSHOT, mimeType = "image/jpeg", contentLength = largeSize, contentId = "abc")
        val filePath = createTempFile()

        every { imageService.getImage(imageId) } returns image
        every { imageService.getFilePath(image) } returns filePath

        val response = imageEndpoint.getScreenshot(imageId, mockRequest)

        assertNotNull(response)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(largeSize, response.headers.contentLength)
        verify(exactly = 1) { imageService.getImage(imageId) }
    }

    @Test
    fun `getCover should handle zero content length`() {
        val imageId = 1L
        val image = Image(id = imageId, type = ImageType.COVER, mimeType = "image/png", contentLength = 0L, contentId = "abc")
        val filePath = createTempFile("")

        every { imageService.getImage(imageId) } returns image
        every { imageService.getFilePath(image) } returns filePath

        val response = imageEndpoint.getCover(imageId, mockRequest)

        assertNotNull(response)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(0L, response.headers.contentLength)
        verify(exactly = 1) { imageService.getImage(imageId) }
    }

    @Test
    fun `getAvatarByUsername should handle empty username`() {
        val username = ""
        val avatar = Image(id = 1L, type = ImageType.AVATAR, mimeType = "image/jpeg", contentLength = 512L, contentId = "avatar-id")
        val filePath = createTempFile("avatar data")

        every { userService.getAvatar(username) } returns avatar
        every { imageService.getImage(1L) } returns avatar
        every { imageService.getFilePath(avatar) } returns filePath

        val response = imageEndpoint.getAvatarByUsername(username, mockRequest)

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

    @Test
    fun `should return 304 Not Modified when ETag matches If-None-Match`() {
        val imageId = 1L
        val contentId = "abc-123"
        val image = Image(id = imageId, type = ImageType.COVER, mimeType = "image/png", contentLength = 2048L, contentId = contentId)
        val requestWithEtag = mockk<HttpServletRequest> {
            every { getHeader(HttpHeaders.IF_NONE_MATCH) } returns "\"$contentId\""
        }

        every { imageService.getImage(imageId) } returns image

        val response = imageEndpoint.getCover(imageId, requestWithEtag)

        assertNotNull(response)
        assertEquals(HttpStatus.NOT_MODIFIED, response.statusCode)
        assertNull(response.body)
        verify(exactly = 1) { imageService.getImage(imageId) }
        verify(exactly = 0) { imageService.getFilePath(any()) }
    }

    @Test
    fun `should return 200 with ETag when If-None-Match does not match`() {
        val imageId = 1L
        val contentId = "abc-123"
        val image = Image(id = imageId, type = ImageType.COVER, mimeType = "image/png", contentLength = 2048L, contentId = contentId)
        val filePath = createTempFile("cover data")
        val requestWithOldEtag = mockk<HttpServletRequest> {
            every { getHeader(HttpHeaders.IF_NONE_MATCH) } returns "\"old-etag\""
        }

        every { imageService.getImage(imageId) } returns image
        every { imageService.getFilePath(image) } returns filePath

        val response = imageEndpoint.getCover(imageId, requestWithOldEtag)

        assertNotNull(response)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals("\"$contentId\"", response.headers.eTag)
        verify(exactly = 1) { imageService.getImage(imageId) }
        verify(exactly = 1) { imageService.getFilePath(image) }
    }

    @Test
    fun `should include ETag and Cache-Control headers in response`() {
        val imageId = 1L
        val contentId = "content-uuid-456"
        val image = Image(id = imageId, type = ImageType.COVER, mimeType = "image/jpeg", contentLength = 1024L, contentId = contentId)
        val filePath = createTempFile()

        every { imageService.getImage(imageId) } returns image
        every { imageService.getFilePath(image) } returns filePath

        val response = imageEndpoint.getCover(imageId, mockRequest)

        assertNotNull(response)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals("\"$contentId\"", response.headers.eTag)
        assertNotNull(response.headers.cacheControl)
    }

    @Test
    fun `should return 304 for weak ETag match`() {
        val imageId = 1L
        val contentId = "abc-123"
        val image = Image(id = imageId, type = ImageType.COVER, mimeType = "image/png", contentLength = 2048L, contentId = contentId)
        val requestWithWeakEtag = mockk<HttpServletRequest> {
            every { getHeader(HttpHeaders.IF_NONE_MATCH) } returns "W/\"$contentId\""
        }

        every { imageService.getImage(imageId) } returns image

        val response = imageEndpoint.getCover(imageId, requestWithWeakEtag)

        assertNotNull(response)
        assertEquals(HttpStatus.NOT_MODIFIED, response.statusCode)
        verify(exactly = 0) { imageService.getFilePath(any()) }
    }
}
