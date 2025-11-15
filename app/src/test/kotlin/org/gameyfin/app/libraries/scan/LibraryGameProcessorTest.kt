package org.gameyfin.app.libraries.scan

import io.mockk.*
import org.gameyfin.app.core.filesystem.FilesystemService
import org.gameyfin.app.games.GameService
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.entities.GameMetadata
import org.gameyfin.app.games.entities.Image
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.media.ImageService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.io.path.Path
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LibraryGameProcessorTest {

    private lateinit var gameService: GameService
    private lateinit var imageService: ImageService
    private lateinit var filesystemService: FilesystemService
    private lateinit var libraryGameProcessor: LibraryGameProcessor

    @BeforeEach
    fun setup() {
        gameService = mockk()
        imageService = mockk()
        filesystemService = mockk()

        libraryGameProcessor = LibraryGameProcessor(
            gameService,
            imageService,
            filesystemService
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `processNewGame should match and persist game successfully`() {
        val path = Path("/path/to/game")
        val library = createTestLibrary(1L)
        val matchedGame = createTestGame(null, path.toString())
        val persistedGame = createTestGame(1L, path.toString())

        every { gameService.matchFromFile(path, library) } returns matchedGame
        every { imageService.downloadIfNew(any()) } just Runs
        every { filesystemService.calculateFileSize(path.toString()) } returns 1024L
        every { gameService.create(listOf(matchedGame)) } returns listOf(persistedGame)

        val result = libraryGameProcessor.processNewGame(path, library)

        assertEquals(persistedGame, result)
        verify(exactly = 1) { gameService.matchFromFile(path, library) }
        verify(exactly = 1) { gameService.create(listOf(matchedGame)) }
    }

    @Test
    fun `processNewGame should download cover image if present`() {
        val path = Path("/path/to/game")
        val library = createTestLibrary(1L)
        val coverImage = createTestImage(1L)
        val matchedGame = createTestGame(null, path.toString(), coverImage = coverImage)
        val persistedGame = createTestGame(1L, path.toString(), coverImage = coverImage)

        every { gameService.matchFromFile(path, library) } returns matchedGame
        every { imageService.downloadIfNew(coverImage) } just Runs
        every { imageService.downloadIfNew(any()) } just Runs
        every { filesystemService.calculateFileSize(path.toString()) } returns 1024L
        every { gameService.create(listOf(matchedGame)) } returns listOf(persistedGame)

        libraryGameProcessor.processNewGame(path, library)

        verify(exactly = 1) { imageService.downloadIfNew(coverImage) }
    }

    @Test
    fun `processNewGame should download header image if present`() {
        val path = Path("/path/to/game")
        val library = createTestLibrary(1L)
        val headerImage = createTestImage(2L)
        val matchedGame = createTestGame(null, path.toString(), headerImage = headerImage)
        val persistedGame = createTestGame(1L, path.toString(), headerImage = headerImage)

        every { gameService.matchFromFile(path, library) } returns matchedGame
        every { imageService.downloadIfNew(headerImage) } just Runs
        every { imageService.downloadIfNew(any()) } just Runs
        every { filesystemService.calculateFileSize(path.toString()) } returns 1024L
        every { gameService.create(listOf(matchedGame)) } returns listOf(persistedGame)

        libraryGameProcessor.processNewGame(path, library)

        verify(exactly = 1) { imageService.downloadIfNew(headerImage) }
    }

    @Test
    fun `processNewGame should download all game images`() {
        val path = Path("/path/to/game")
        val library = createTestLibrary(1L)
        val image1 = createTestImage(1L)
        val image2 = createTestImage(2L)
        val matchedGame = createTestGame(null, path.toString(), images = mutableListOf(image1, image2))
        val persistedGame = createTestGame(1L, path.toString(), images = mutableListOf(image1, image2))

        every { gameService.matchFromFile(path, library) } returns matchedGame
        every { imageService.downloadIfNew(any()) } just Runs
        every { filesystemService.calculateFileSize(path.toString()) } returns 1024L
        every { gameService.create(listOf(matchedGame)) } returns listOf(persistedGame)

        libraryGameProcessor.processNewGame(path, library)

        verify(exactly = 1) { imageService.downloadIfNew(image1) }
        verify(exactly = 1) { imageService.downloadIfNew(image2) }
    }

    @Test
    fun `processNewGame should calculate file size`() {
        val path = Path("/path/to/game")
        val library = createTestLibrary(1L)
        val matchedGame = createTestGame(null, path.toString())
        val persistedGame = createTestGame(1L, path.toString())
        val expectedSize = 2048L

        every { gameService.matchFromFile(path, library) } returns matchedGame
        every { imageService.downloadIfNew(any()) } just Runs
        every { filesystemService.calculateFileSize(path.toString()) } returns expectedSize
        every { gameService.create(listOf(matchedGame)) } returns listOf(persistedGame)

        libraryGameProcessor.processNewGame(path, library)

        assertEquals(expectedSize, matchedGame.metadata.fileSize)
        verify(exactly = 1) { filesystemService.calculateFileSize(path.toString()) }
    }

    @Test
    fun `processNewGame should cleanup images on failure`() {
        val path = Path("/path/to/game")
        val library = createTestLibrary(1L)
        val coverImage = createTestImage(1L)
        val matchedGame = createTestGame(null, path.toString(), coverImage = coverImage)

        every { gameService.matchFromFile(path, library) } returns matchedGame
        every { imageService.downloadIfNew(coverImage) } just Runs
        every { imageService.downloadIfNew(any()) } just Runs
        every { filesystemService.calculateFileSize(path.toString()) } returns 1024L
        every { gameService.create(listOf(matchedGame)) } throws RuntimeException("Persistence error")
        every { imageService.deleteImageIfUnused(coverImage) } just Runs

        assertThrows(RuntimeException::class.java) {
            libraryGameProcessor.processNewGame(path, library)
        }

        verify(exactly = 1) { imageService.deleteImageIfUnused(coverImage) }
    }

    @Test
    fun `processNewGame should handle cleanup failure silently`() {
        val path = Path("/path/to/game")
        val library = createTestLibrary(1L)
        val coverImage = createTestImage(1L)
        val matchedGame = createTestGame(null, path.toString(), coverImage = coverImage)

        every { gameService.matchFromFile(path, library) } returns matchedGame
        every { imageService.downloadIfNew(any()) } just Runs
        every { filesystemService.calculateFileSize(path.toString()) } returns 1024L
        every { gameService.create(listOf(matchedGame)) } throws RuntimeException("Persistence error")
        every { imageService.deleteImageIfUnused(any()) } throws RuntimeException("Cleanup error")

        assertThrows(RuntimeException::class.java) {
            libraryGameProcessor.processNewGame(path, library)
        }
    }

    @Test
    fun `processExistingGame should update game successfully`() {
        val game = createTestGame(1L, "/path/to/game")
        val updatedGame = createTestGame(1L, "/path/to/game")

        every { gameService.update(game) } returns updatedGame
        every { imageService.downloadIfNew(any()) } just Runs
        every { filesystemService.calculateFileSize("/path/to/game") } returns 1024L

        val result = libraryGameProcessor.processExistingGame(game)

        assertEquals(updatedGame, result)
        verify(exactly = 1) { gameService.update(game) }
    }

    @Test
    fun `processExistingGame should return null when game is not updated`() {
        val game = createTestGame(1L, "/path/to/game")

        every { gameService.update(game) } returns null

        val result = libraryGameProcessor.processExistingGame(game)

        assertEquals(null, result)
        verify(exactly = 1) { gameService.update(game) }
    }

    @Test
    fun `processExistingGame should download images after update`() {
        val game = createTestGame(1L, "/path/to/game")
        val coverImage = createTestImage(1L)
        val updatedGame = createTestGame(1L, "/path/to/game", coverImage = coverImage)

        every { gameService.update(game) } returns updatedGame
        every { imageService.downloadIfNew(coverImage) } just Runs
        every { imageService.downloadIfNew(any()) } just Runs
        every { filesystemService.calculateFileSize("/path/to/game") } returns 1024L

        libraryGameProcessor.processExistingGame(game)

        verify(exactly = 1) { imageService.downloadIfNew(coverImage) }
    }

    @Test
    fun `processExistingGame should recalculate file size`() {
        val game = createTestGame(1L, "/path/to/game")
        val updatedGame = createTestGame(1L, "/path/to/game")
        val newSize = 4096L

        every { gameService.update(game) } returns updatedGame
        every { imageService.downloadIfNew(any()) } just Runs
        every { filesystemService.calculateFileSize("/path/to/game") } returns newSize

        libraryGameProcessor.processExistingGame(game)

        assertEquals(newSize, updatedGame.metadata.fileSize)
        verify(exactly = 1) { filesystemService.calculateFileSize("/path/to/game") }
    }

    @Test
    fun `processExistingGame should cleanup images on failure`() {
        val game = createTestGame(1L, "/path/to/game")
        val coverImage = createTestImage(1L)
        val updatedGame = createTestGame(1L, "/path/to/game", coverImage = coverImage)

        every { gameService.update(game) } returns updatedGame
        every { imageService.downloadIfNew(coverImage) } just Runs
        every { imageService.downloadIfNew(any()) } just Runs
        every { filesystemService.calculateFileSize("/path/to/game") } throws RuntimeException("File error")
        every { imageService.deleteImageIfUnused(coverImage) } just Runs

        assertThrows(RuntimeException::class.java) {
            libraryGameProcessor.processExistingGame(game)
        }

        verify(exactly = 1) { imageService.deleteImageIfUnused(coverImage) }
    }

    @Test
    fun `processExistingGame should throw exception on update failure`() {
        val game = createTestGame(1L, "/path/to/game")

        every { gameService.update(game) } throws RuntimeException("Update error")

        assertThrows(RuntimeException::class.java) {
            libraryGameProcessor.processExistingGame(game)
        }
    }

    @Test
    fun `processExistingGame should not download images when game is not updated`() {
        val game = createTestGame(1L, "/path/to/game")

        every { gameService.update(game) } returns null

        libraryGameProcessor.processExistingGame(game)

        verify(exactly = 0) { imageService.downloadIfNew(any()) }
        verify(exactly = 0) { filesystemService.calculateFileSize(any()) }
    }

    @Test
    fun `processExistingGame should handle multiple images`() {
        val game = createTestGame(1L, "/path/to/game")
        val image1 = createTestImage(1L)
        val image2 = createTestImage(2L)
        val image3 = createTestImage(3L)
        val updatedGame = createTestGame(
            1L,
            "/path/to/game",
            images = mutableListOf(image1, image2, image3)
        )

        every { gameService.update(game) } returns updatedGame
        every { imageService.downloadIfNew(any()) } just Runs
        every { filesystemService.calculateFileSize("/path/to/game") } returns 1024L

        libraryGameProcessor.processExistingGame(game)

        verify(exactly = 1) { imageService.downloadIfNew(image1) }
        verify(exactly = 1) { imageService.downloadIfNew(image2) }
        verify(exactly = 1) { imageService.downloadIfNew(image3) }
    }

    @Test
    fun `processNewGame should handle game without images`() {
        val path = Path("/path/to/game")
        val library = createTestLibrary(1L)
        val matchedGame = createTestGame(null, path.toString())
        val persistedGame = createTestGame(1L, path.toString())

        every { gameService.matchFromFile(path, library) } returns matchedGame
        every { filesystemService.calculateFileSize(path.toString()) } returns 1024L
        every { gameService.create(listOf(matchedGame)) } returns listOf(persistedGame)

        val result = libraryGameProcessor.processNewGame(path, library)

        assertNotNull(result)
        verify(exactly = 0) { imageService.downloadIfNew(any()) }
    }

    private fun createTestLibrary(id: Long): Library {
        return mockk<Library>(relaxed = true) {
            every { this@mockk.id } returns id
        }
    }

    private fun createTestGame(
        id: Long?,
        path: String,
        coverImage: Image? = null,
        headerImage: Image? = null,
        images: MutableList<Image> = mutableListOf()
    ): Game {
        val metadata = GameMetadata(path = path)
        return mockk<Game>(relaxed = true) {
            every { this@mockk.id } returns id
            every { this@mockk.metadata } returns metadata
            every { this@mockk.coverImage } returns coverImage
            every { this@mockk.headerImage } returns headerImage
            every { this@mockk.images } returns images
        }
    }

    private fun createTestImage(id: Long): Image {
        return mockk<Image>(relaxed = true) {
            every { this@mockk.id } returns id
        }
    }
}

