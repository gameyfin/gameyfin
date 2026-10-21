package org.gameyfin.app.games.extensions

import io.mockk.*
import org.gameyfin.app.core.plugins.management.PluginManagementEntry
import org.gameyfin.app.core.security.isCurrentUserAdmin
import org.gameyfin.app.games.dto.GameAdminDto
import org.gameyfin.app.games.dto.GameField
import org.gameyfin.app.games.dto.GameUserDto
import org.gameyfin.app.games.entities.Company
import org.gameyfin.app.games.entities.CompanyType
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.media.Image
import org.gameyfin.app.media.ImageType
import org.gameyfin.pluginapi.gamemetadata.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameExtensionsTest {

    private lateinit var library: Library
    private lateinit var game: Game

    @BeforeEach
    fun setup() {
        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")

        library = mockk(relaxed = true) {
            every { id } returns 1L
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `toDto should return GameAdminDto when user is admin`() {
        every { isCurrentUserAdmin() } returns true
        game = createTestGame()

        val result = game.toDto()

        assertTrue(result is GameAdminDto)
    }

    @Test
    fun `toDto should return GameUserDto when user is not admin`() {
        every { isCurrentUserAdmin() } returns false
        game = createTestGame()

        val result = game.toDto()

        assertTrue(result is GameUserDto)
    }

    @Test
    fun `toDtos should return list of GameAdminDto when user is admin`() {
        every { isCurrentUserAdmin() } returns true
        val games = listOf(createTestGame(), createTestGame(id = 2L))

        val result = games.toDtos()

        assertEquals(2, result.size)
        assertTrue(result.all { it is GameAdminDto })
    }

    @Test
    fun `toDtos should return list of GameUserDto when user is not admin`() {
        every { isCurrentUserAdmin() } returns false
        val games = listOf(createTestGame(), createTestGame(id = 2L))

        val result = games.toDtos()

        assertEquals(2, result.size)
        assertTrue(result.all { it is GameUserDto })
    }

    @Test
    fun `toDtos should return empty list for empty collection`() {
        every { isCurrentUserAdmin() } returns false
        val games = emptyList<Game>()

        val result = games.toDtos()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `toAdminDto should map all fields correctly`() {
        game = createTestGame()

        val result = game.toAdminDto()

        assertEquals(1L, result.id)
        assertEquals(1L, result.libraryId)
        assertEquals("Test Game", result.title)
        assertEquals(listOf(Platform.PC_MICROSOFT_WINDOWS), result.platforms)
        assertEquals(10L, result.cover!!.id)
        assertEquals(11L, result.header!!.id)
        assertEquals("Test comment", result.comment)
        assertEquals("Test summary", result.summary)
        assertNotNull(result.release)
        assertEquals(85, result.userRating)
        assertEquals(90, result.criticRating)
        assertEquals(listOf("Publisher1"), result.publishers)
        assertEquals(listOf("Developer1"), result.developers)
        assertEquals(listOf(Genre.ACTION), result.genres)
        assertEquals(listOf(Theme.FANTASY), result.themes)
        assertEquals(listOf("keyword1"), result.keywords)
        assertEquals(listOf(GameFeature.SINGLEPLAYER), result.features)
        assertEquals(listOf(PlayerPerspective.FIRST_PERSON), result.perspectives)
        assertEquals(listOf(12L), result.images!!.map { it.id })
        assertEquals(listOf("https://example.com/video"), result.videoUrls)
        assertNotNull(result.metadata)
    }

    @Test
    fun `toUserDto should map all fields correctly`() {
        game = createTestGame()

        val result = game.toUserDto()

        assertEquals(1L, result.id)
        assertEquals(1L, result.libraryId)
        assertEquals("Test Game", result.title)
        assertEquals(listOf(Platform.PC_MICROSOFT_WINDOWS), result.platforms)
        assertEquals(10L, result.cover!!.id)
        assertEquals(11L, result.header!!.id)
        assertEquals("Test comment", result.comment)
        assertEquals("Test summary", result.summary)
        assertNotNull(result.release)
        assertEquals(85, result.userRating)
        assertEquals(90, result.criticRating)
        assertEquals(listOf("Publisher1"), result.publishers)
        assertEquals(listOf("Developer1"), result.developers)
        assertEquals(listOf(Genre.ACTION), result.genres)
        assertEquals(listOf(Theme.FANTASY), result.themes)
        assertEquals(listOf("keyword1"), result.keywords)
        assertEquals(listOf(GameFeature.SINGLEPLAYER), result.features)
        assertEquals(listOf(PlayerPerspective.FIRST_PERSON), result.perspectives)
        assertEquals(listOf(12L), result.images!!.map { it.id })
        assertEquals(listOf("https://example.com/video"), result.videoUrls)
        assertNotNull(result.metadata)
    }

    @Test
    fun `toAdminDto should handle null optional fields`() {
        game = Game(
            id = 1L,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            library = library,
            title = "Test Game",
            platforms = mutableListOf(),
            metadata = org.gameyfin.app.games.entities.GameMetadata(path = "/test/path")
        )

        val result = game.toAdminDto()

        assertEquals("Test Game", result.title)
        assertEquals(null, result.cover?.id)
        assertEquals(null, result.header?.id)
        assertEquals(null, result.comment)
        assertEquals(null, result.summary)
        assertEquals(null, result.release)
        assertEquals(null, result.userRating)
        assertEquals(null, result.criticRating)
    }

    @Test
    fun `toUserDto should handle null optional fields`() {
        game = Game(
            id = 1L,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            library = library,
            title = "Test Game",
            platforms = mutableListOf(),
            metadata = org.gameyfin.app.games.entities.GameMetadata(path = "/test/path")
        )

        val result = game.toUserDto()

        assertEquals("Test Game", result.title)
        assertEquals(null, result.cover?.id)
        assertEquals(null, result.header?.id)
        assertEquals(null, result.comment)
        assertEquals(null, result.summary)
        assertEquals(null, result.release)
        assertEquals(null, result.userRating)
        assertEquals(null, result.criticRating)
    }

    @Test
    fun `toAdminDto should convert release date to LocalDate in UTC`() {
        val releaseInstant = Instant.parse("2023-06-15T12:30:00Z")
        game = Game(
            id = 1L,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            library = library,
            title = "Test Game",
            platforms = mutableListOf(),
            release = releaseInstant,
            metadata = org.gameyfin.app.games.entities.GameMetadata(path = "/test/path")
        )

        val result = game.toAdminDto()

        val expectedDate = releaseInstant.atZone(ZoneOffset.UTC).toLocalDate()
        assertEquals(expectedDate, result.release)
    }

    @Test
    fun `toAdminDto should map video URLs to strings`() {
        game = Game(
            id = 1L,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            library = library,
            title = "Test Game",
            platforms = mutableListOf(),
            videoUrls = listOf(URI("https://example.com/video1"), URI("https://example.com/video2")),
            metadata = org.gameyfin.app.games.entities.GameMetadata(path = "/test/path")
        )

        val result = game.toAdminDto()

        assertEquals(listOf("https://example.com/video1", "https://example.com/video2"), result.videoUrls)
    }

    @Test
    fun `GameMetadata toAdminDto should include admin-specific fields`() {
        val pluginEntry = mockk<PluginManagementEntry> {
            every { pluginId } returns "test-plugin"
        }
        val metadata = org.gameyfin.app.games.entities.GameMetadata(
            path = "/test/path",
            fileSize = 1000L,
            downloadCount = 5,
            matchConfirmed = true,
            originalIds = mapOf(pluginEntry to "ext-123")
        )

        val result = metadata.toAdminDto()

        assertEquals("/test/path", result.path)
        assertEquals(1000L, result.fileSize)
        assertEquals(5, result.downloadCount)
        assertEquals(true, result.matchConfirmed)
        assertEquals(mapOf("test-plugin" to "ext-123"), result.originalIds)
    }

    @Test
    fun `GameMetadata toUserDto should only include fileSize`() {
        val metadata = org.gameyfin.app.games.entities.GameMetadata(
            path = "/test/path",
            fileSize = 2000L,
            downloadCount = 10,
            matchConfirmed = false
        )

        val result = metadata.toUserDto()

        assertEquals(2000L, result.fileSize)
    }

    @Test
    fun `GameMetadata toAdminDto should handle null fileSize as 0`() {
        val metadata = org.gameyfin.app.games.entities.GameMetadata(
            path = "/test/path",
            fileSize = null
        )

        val result = metadata.toAdminDto()

        assertEquals(0L, result.fileSize)
    }

    @Test
    fun `toSummaryDto should map overview fields and admin-only fields for admins`() {
        game = createTestGame(5L)
        game.metadata.fileSize = 1234L
        game.metadata.downloadCount = 7
        game.metadata.matchConfirmed = true

        val result = game.toSummaryDto(admin = true)

        assertEquals(5L, result.id)
        assertEquals(1L, result.libraryId)
        assertEquals("Test Game", result.title)
        assertEquals(listOf(Platform.PC_MICROSOFT_WINDOWS), result.platforms)
        assertEquals(10L, result.cover?.id)
        assertEquals(11L, result.header?.id)
        assertEquals(12L, result.previewImage?.id)
        assertEquals(listOf("Publisher1"), result.publishers)
        assertEquals(listOf("Developer1"), result.developers)
        assertEquals(1234L, result.fileSize)
        assertEquals("/test/path", result.path)
        assertEquals(7, result.downloadCount)
        assertEquals(true, result.matchConfirmed)
    }

    @Test
    fun `toSummaryDto should omit admin-only fields for users`() {
        game = createTestGame()

        val result = game.toSummaryDto(admin = false)

        assertNull(result.path)
        assertNull(result.downloadCount)
        assertNull(result.matchConfirmed)
    }

    @Test
    fun `toSummaryDto should map empty lists to null`() {
        game = createTestGame()
        game.publishers.clear()
        game.images.clear()
        game.genres = emptyList()

        val result = game.toSummaryDto(admin = false)

        assertNull(result.publishers)
        assertNull(result.genres)
        assertNull(result.previewImage)
    }

    @Test
    fun `toPatchDto should only contain requested fields`() {
        game = createTestGame()

        val result = game.toPatchDto(setOf(GameField.TITLE, GameField.IMAGES), admin = false)

        assertNotNull(result)
        assertEquals(listOf(GameField.TITLE, GameField.IMAGES), result.fields)
        assertEquals("Test Game", result.values.title)
        assertEquals(listOf(12L), result.values.images?.map { it.id })
        assertEquals(12L, result.values.previewImage?.id)
        assertNull(result.values.summary)
        assertNull(result.values.platforms)
        assertEquals(game.updatedAt, result.updatedAt)
    }

    @Test
    fun `toPatchDto should drop admin-only fields for users`() {
        game = createTestGame()

        val result = game.toPatchDto(setOf(GameField.PATH, GameField.DOWNLOAD_COUNT, GameField.FILE_SIZE), admin = false)

        assertNotNull(result)
        assertEquals(listOf(GameField.FILE_SIZE), result.fields)
        assertNull(result.values.path)
        assertNull(result.values.downloadCount)
    }

    @Test
    fun `toPatchDto should include admin-only fields for admins`() {
        game = createTestGame()
        game.metadata.downloadCount = 3

        val result = game.toPatchDto(setOf(GameField.PATH, GameField.DOWNLOAD_COUNT), admin = true)

        assertNotNull(result)
        assertEquals(listOf(GameField.PATH, GameField.DOWNLOAD_COUNT), result.fields)
        assertEquals("/test/path", result.values.path)
        assertEquals(3, result.values.downloadCount)
    }

    @Test
    fun `toPatchDto should return null if no field is visible`() {
        game = createTestGame()

        assertNull(game.toPatchDto(setOf(GameField.MATCH_CONFIRMED, GameField.ORIGINAL_IDS), admin = false))
        assertNull(game.toPatchDto(emptySet(), admin = true))
    }

    private fun createTestGame(id: Long = 1L): Game {
        val coverImage = mockk<Image> {
            every { this@mockk.id } returns 10L
            every { type } returns ImageType.COVER
            every { blurhash } returns "mockedBlurhash"
        }
        val headerImage = mockk<Image> {
            every { this@mockk.id } returns 11L
            every { type } returns ImageType.HEADER
            every { blurhash } returns "mockedBlurhash"
        }
        val image = mockk<Image> {
            every { this@mockk.id } returns 12L
            every { type } returns ImageType.SCREENSHOT
            every { blurhash } returns "mockedBlurhash"
        }
        val publisher = Company(id = 1L, name = "Publisher1", type = CompanyType.PUBLISHER)
        val developer = Company(id = 2L, name = "Developer1", type = CompanyType.DEVELOPER)

        return Game(
            id = id,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            library = library,
            title = "Test Game",
            platforms = mutableListOf(Platform.PC_MICROSOFT_WINDOWS),
            coverImage = coverImage,
            headerImage = headerImage,
            comment = "Test comment",
            summary = "Test summary",
            release = Instant.parse("2023-06-15T00:00:00Z"),
            userRating = 85,
            criticRating = 90,
            publishers = mutableListOf(publisher),
            developers = mutableListOf(developer),
            genres = listOf(Genre.ACTION),
            themes = listOf(Theme.FANTASY),
            keywords = listOf("keyword1"),
            features = listOf(GameFeature.SINGLEPLAYER),
            perspectives = listOf(PlayerPerspective.FIRST_PERSON),
            images = mutableListOf(image),
            videoUrls = listOf(URI("https://example.com/video")),
            metadata = org.gameyfin.app.games.entities.GameMetadata(path = "/test/path")
        )
    }
}

