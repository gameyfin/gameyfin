package org.gameyfin.app.games

import io.mockk.*
import org.gameyfin.app.games.dto.GameImageSelectionDto
import org.gameyfin.app.games.dto.GameUpdateDto
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.entities.GameFieldUserSource
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.media.Image
import org.gameyfin.app.media.ImageService
import org.gameyfin.app.media.ImageType
import org.gameyfin.app.users.UserService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.User as SecurityUser
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*

class GameMediaTest {
    private val repository = mockk<GameRepository>()
    private val imageService = mockk<ImageService>()
    private val userService = mockk<UserService>()
    private val service = GameService(repository, mockk(), mockk(), mockk(), mockk(), userService, imageService, mockk())
    private val first = Image(id = 11, type = ImageType.SCREENSHOT)
    private val second = Image(id = 12, type = ImageType.SCREENSHOT)
    private lateinit var game: Game

    @BeforeEach
    fun setup() {
        game = Game(Path.of("/games/test"), Library(name = "Test")).apply {
            id = 1
            images = mutableListOf(first, second)
            coverImage = Image(id = 10, type = ImageType.COVER)
            videoUrls = listOf(URI("https://example.com/old.mp4"))
        }
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            SecurityUser.withUsername("admin").password("unused").roles("ADMIN").build(), "unused"
        )
        every { userService.getByUsernameNonNull("admin") } returns mockk(relaxed = true)
        every { repository.findById(1) } returns java.util.Optional.of(game)
        every { repository.save(game) } returns game
    }

    @AfterEach
    fun cleanup() {
        SecurityContextHolder.clearContext()
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization()
    }

    @Test
    fun `mixed media order can change independently of screenshot and video lists`() {
        val order = listOf("image", "video", "image")
        service.edit(update().copy(mediaOrder = order))
        assertEquals(order, game.mediaOrder)
        assertEquals(listOf(first, second), game.images)
        assertEquals(1, game.videoUrls.size)
    }

    @Test
    fun `reject invalid media order before saving`() {
        listOf(listOf("video"), listOf("image", "audio", "image"), listOf("image", "video", "image", "video"))
            .forEach { order ->
                assertFailsWith<IllegalArgumentException> { service.edit(update().copy(mediaOrder = order)) }
            }
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `reorder existing screenshots and videos and create user metadata`() {
        val urls = listOf("https://example.com/second.mp4", "https://youtu.be/abcdefghijk")
        service.edit(update(images = listOf(GameImageSelectionDto(id = 12), GameImageSelectionDto(id = 11)), videos = urls))
        assertEquals(listOf(second, first), game.images)
        assertEquals(urls, game.videoUrls.map { it.toString() })
        assertIs<GameFieldUserSource>(game.metadata.fields.getValue("images").source)
        assertIs<GameFieldUserSource>(game.metadata.fields.getValue("videoUrls").source)
        verify(exactly = 0) { imageService.downloadIfNew(any()) }
    }

    @Test
    fun `empty lists clear media while omitted lists leave media untouched`() {
        service.edit(update())
        assertEquals(listOf(first, second), game.images)
        assertEquals(1, game.videoUrls.size)
        service.edit(update(images = emptyList(), videos = emptyList()))
        assertTrue(game.images.isEmpty())
        assertTrue(game.videoUrls.isEmpty())
    }

    @Test
    fun `mix uploaded and searched screenshots with existing images in requested order`() {
        val file = png()
        val uploaded = Image(id = 20, type = ImageType.SCREENSHOT)
        val searched = Image(id = 21, type = ImageType.SCREENSHOT, originalUrl = "https://example.com/screen.png")
        every { imageService.createFromInputStream(ImageType.SCREENSHOT, any(), "image/png") } returns uploaded
        every { imageService.createOrGet(match { it.originalUrl == searched.originalUrl }) } returns searched
        every { imageService.downloadIfNew(searched) } just Runs
        service.edit(update(images = listOf(
            GameImageSelectionDto(uploadKey = "new"), GameImageSelectionDto(url = searched.originalUrl), GameImageSelectionDto(id = 11)
        )), mapOf("new" to file))
        assertEquals(listOf(uploaded, searched, first), game.images)
    }

    @Test
    fun `replace cover and banner with uploads whose MIME type is detected from bytes`() {
        val cover = Image(id = 20, type = ImageType.COVER)
        val banner = Image(id = 21, type = ImageType.HEADER)
        every { imageService.createFromInputStream(ImageType.COVER, any(), "image/png") } returns cover
        every { imageService.createFromInputStream(ImageType.HEADER, any(), "image/png") } returns banner
        service.edit(update(cover = GameImageSelectionDto(uploadKey = "cover"), header = GameImageSelectionDto(uploadKey = "banner")),
            mapOf("cover" to png(), "banner" to png()))
        assertSame(cover, game.coverImage)
        assertSame(banner, game.headerImage)
        assertIs<GameFieldUserSource>(game.metadata.fields.getValue("coverImage").source)
        assertIs<GameFieldUserSource>(game.metadata.fields.getValue("headerImage").source)
    }

    @Test
    fun `reject foreign or wrong-role image IDs and ambiguous selections`() {
        val invalid = listOf(
            update(images = listOf(GameImageSelectionDto(id = 999))),
            update(images = listOf(GameImageSelectionDto(id = 10))),
            update(cover = GameImageSelectionDto(id = 11)),
            update(images = listOf(GameImageSelectionDto(id = 11, url = "https://example.com/a.png"))),
            update(images = listOf(GameImageSelectionDto()))
        )
        invalid.forEach { assertFailsWith<IllegalArgumentException> { service.edit(it) } }
        assertEquals(listOf(first, second), game.images)
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `reject duplicate missing or unreferenced images before writing`() {
        assertFailsWith<IllegalArgumentException> {
            service.edit(update(images = listOf(GameImageSelectionDto(id = 11), GameImageSelectionDto(id = 11))))
        }
        assertFailsWith<IllegalArgumentException> {
            service.edit(update(cover = GameImageSelectionDto(uploadKey = "missing")))
        }
        assertFailsWith<IllegalArgumentException> { service.edit(update(), mapOf("unused" to png())) }
        assertFailsWith<IllegalArgumentException> { service.edit(update(videos = listOf("javascript:alert(1)"))) }
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `reject non-image files even with an image content type`() {
        assertFailsWith<IllegalArgumentException> {
            service.edit(update(cover = GameImageSelectionDto(uploadKey = "cover")), mapOf(
                "cover" to MockMultipartFile("cover", "cover.png", "image/png", "not an image".toByteArray())
            ))
        }
        assertEquals(10L, game.coverImage?.id)
        verify(exactly = 0) { imageService.createFromInputStream(any(), any(), any()) }
    }

    @Test
    fun `removed screenshot cleanup runs only after a successful commit`() {
        every { imageService.deleteImageIfUnused(any()) } just Runs
        TransactionSynchronizationManager.initSynchronization()
        service.edit(update(images = listOf(GameImageSelectionDto(id = 12))))
        verify(exactly = 0) { imageService.deleteImageIfUnused(any()) }
        val callbacks = TransactionSynchronizationManager.getSynchronizations()
        callbacks.forEach { it.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK) }
        verify(exactly = 0) { imageService.deleteImageIfUnused(any()) }
        callbacks.forEach { it.afterCompletion(TransactionSynchronization.STATUS_COMMITTED) }
        verify(exactly = 1) { imageService.deleteImageIfUnused(first) }
    }

    private fun png(): MockMultipartFile {
        val bytes = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", it) }.toByteArray()
        return MockMultipartFile("file", "image.png", "text/plain", bytes)
    }

    private fun update(
        images: List<GameImageSelectionDto>? = null, videos: List<String>? = null,
        cover: GameImageSelectionDto? = null, header: GameImageSelectionDto? = null
    ) = GameUpdateDto(
        id = 1, title = null, platforms = null, release = null, coverUrl = null, headerUrl = null,
        comment = null, summary = null, developers = null, publishers = null, genres = null, themes = null,
        features = null, perspectives = null, keywords = null, metadata = null,
        cover = cover, header = header, images = images, videoUrls = videos
    )
}
