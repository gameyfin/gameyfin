package org.gameyfin.app.games

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.multipart.MultipartFile
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GameMediaValidationTest {
    @Test
    fun `detect supported image formats from content`() {
        for ((format, mime) in listOf("png" to "image/png", "jpeg" to "image/jpeg", "gif" to "image/gif")) {
            val output = ByteArrayOutputStream()
            ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, output)
            assertEquals(mime, GameMediaValidation.upload(MockMultipartFile("file", "test.txt", "text/plain", output.toByteArray())))
        }
        val webp = Base64.getDecoder().decode("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA")
        assertEquals("image/webp", GameMediaValidation.upload(MockMultipartFile("file", webp)))
    }

    @Test
    fun `reject empty oversized truncated or non-image files`() {
        val empty = MockMultipartFile("file", byteArrayOf())
        val oversized = mockk<MultipartFile> { every { size } returns GameMediaValidation.MAX_FILE_SIZE + 1 }
        val text = MockMultipartFile("file", "test.png", "image/png", "not an image".toByteArray())
        val truncated = MockMultipartFile("file", byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))
        for (file in listOf(empty, oversized, text, truncated)) {
            assertFailsWith<IllegalArgumentException> { GameMediaValidation.upload(file) }
        }
    }

    @Test
    fun `malformed URLs produce validation errors`() {
        for (url in listOf("not a URL", "https://example.com/invalid path", "/relative", "javascript:alert(1)")) {
            assertFailsWith<IllegalArgumentException> { GameMediaValidation.httpUrl(url) }
        }
    }
}
