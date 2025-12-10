package org.gameyfin.app.libraries.extensions

import io.mockk.*
import org.gameyfin.app.core.security.isCurrentUserAdmin
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.entities.GameMetadata
import org.gameyfin.app.libraries.dto.LibraryAdminDto
import org.gameyfin.app.libraries.dto.LibraryUserDto
import org.gameyfin.app.libraries.entities.DirectoryMapping
import org.gameyfin.app.libraries.entities.IgnoredPath
import org.gameyfin.app.libraries.entities.IgnoredPathPluginSource
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LibraryExtensionsTest {

    @BeforeEach
    fun setup() {
        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `toDto should return AdminDto when user is admin`() {
        every { isCurrentUserAdmin() } returns true
        val library = createTestLibrary(1L)

        val result = library.toDto()

        assertIs<LibraryAdminDto>(result)
        assertEquals(1L, result.id)
    }

    @Test
    fun `toDto should return UserDto when user is not admin`() {
        every { isCurrentUserAdmin() } returns false
        val library = createTestLibrary(1L)

        val result = library.toDto()

        assertIs<LibraryUserDto>(result)
        assertEquals(1L, result.id)
    }

    @Test
    fun `toDtos should return list of AdminDto when user is admin`() {
        every { isCurrentUserAdmin() } returns true
        val libraries = listOf(createTestLibrary(1L), createTestLibrary(2L))

        val result = libraries.toDtos()

        assertEquals(2, result.size)
        assertTrue(result.all { it is LibraryAdminDto })
    }

    @Test
    fun `toDtos should return list of UserDto when user is not admin`() {
        every { isCurrentUserAdmin() } returns false
        val libraries = listOf(createTestLibrary(1L), createTestLibrary(2L))

        val result = libraries.toDtos()

        assertEquals(2, result.size)
        assertTrue(result.all { it is LibraryUserDto })
    }

    @Test
    fun `toDtos should return empty list when no libraries`() {
        every { isCurrentUserAdmin() } returns true
        val libraries = emptyList<Library>()

        val result = libraries.toDtos()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `toUserDto should create UserDto with correct properties`() {
        val game1 = createTestGame(1L)
        val game2 = createTestGame(2L)
        val library = createTestLibrary(
            id = 1L,
            name = "Test Library",
            games = mutableListOf(game1, game2)
        )

        val result = library.toUserDto()

        assertEquals(1L, result.id)
        assertEquals("Test Library", result.name)
        assertEquals(listOf(1L, 2L), result.gameIds)
    }

    @Test
    fun `toUserDto should handle library with no games`() {
        val library = createTestLibrary(id = 1L, games = mutableListOf())

        val result = library.toUserDto()

        assertEquals(1L, result.id)
        assertTrue(result.gameIds!!.isEmpty())
    }

    @Test
    fun `toUserDto should exclude games with null ids`() {
        val game1 = createTestGame(1L)
        val game2 = createTestGame(null)
        val game3 = createTestGame(3L)
        val library = createTestLibrary(
            id = 1L,
            games = mutableListOf(game1, game2, game3)
        )

        val result = library.toUserDto()

        assertEquals(2, result.gameIds!!.size)
        assertEquals(listOf(1L, 3L), result.gameIds)
    }

    @Test
    fun `toAdminDto should create AdminDto with correct properties`() {
        val dir1 = createDirectoryMapping("/path1", "/ext1")
        val dir2 = createDirectoryMapping("/path2", "/ext2")
        val game1 = createTestGame(1L)
        val game2 = createTestGame(2L)

        val ignoredPath1 = IgnoredPath(id = 0L, path = "/unmatched1", source = IgnoredPathPluginSource(mutableListOf()))
        val ignoredPath2 = IgnoredPath(id = 1L, path = "/unmatched2", source = IgnoredPathPluginSource(mutableListOf()))

        val library = createTestLibrary(
            id = 1L,
            name = "Admin Library",
            directories = mutableListOf(dir1, dir2),
            platforms = mutableListOf(Platform.PC_MICROSOFT_WINDOWS, Platform.PLAYSTATION_5),
            games = mutableListOf(game1, game2),
            ignoredPaths = mutableListOf(ignoredPath1, ignoredPath2)
        )
        game1.metadata.downloadCount = 5
        game2.metadata.downloadCount = 10

        val result = library.toAdminDto()

        assertEquals(1L, result.id)
        assertEquals("Admin Library", result.name)
        assertEquals(2, result.directories.size)
        assertEquals("/path1", result.directories[0].internalPath)
        assertEquals("/ext1", result.directories[0].externalPath)
        assertEquals(2, result.platforms.size)
        assertTrue(result.platforms.contains(Platform.PC_MICROSOFT_WINDOWS))
        assertEquals(listOf(1L, 2L), result.gameIds)
        assertNotNull(result.stats)
        assertEquals(2, result.stats.gamesCount)
        assertEquals(15, result.stats.downloadedGamesCount)
        assertEquals(2, result.ignoredPaths?.size ?: 0)
    }

    @Test
    fun `toAdminDto should handle library with no directories`() {
        val library = createTestLibrary(
            id = 1L,
            directories = mutableListOf()
        )

        val result = library.toAdminDto()

        assertTrue(result.directories.isEmpty())
    }

    @Test
    fun `toAdminDto should handle library with no platforms`() {
        val library = createTestLibrary(
            id = 1L,
            platforms = mutableListOf()
        )

        val result = library.toAdminDto()

        assertTrue(result.platforms.isEmpty())
    }

    @Test
    fun `toAdminDto should handle library with no games`() {
        val library = createTestLibrary(
            id = 1L,
            games = mutableListOf()
        )

        val result = library.toAdminDto()

        assertTrue(result.gameIds!!.isEmpty())
        assertNotNull(result.stats)
        assertEquals(0, result.stats.gamesCount)
        assertEquals(0, result.stats.downloadedGamesCount)
    }

    @Test
    fun `toAdminDto should exclude games with null ids`() {
        val game1 = createTestGame(1L)
        val game2 = createTestGame(null)
        val game3 = createTestGame(3L)
        val library = createTestLibrary(
            id = 1L,
            games = mutableListOf(game1, game2, game3)
        )

        val result = library.toAdminDto()

        assertEquals(2, result.gameIds!!.size)
        assertEquals(listOf(1L, 3L), result.gameIds)
    }

    @Test
    fun `toAdminDto should calculate stats correctly`() {
        val game1 = createTestGame(1L)
        val game2 = createTestGame(2L)
        val game3 = createTestGame(3L)
        val library = createTestLibrary(
            id = 1L,
            games = mutableListOf(game1, game2, game3)
        )
        game1.metadata.downloadCount = 3
        game2.metadata.downloadCount = 7
        game3.metadata.downloadCount = 2

        val result = library.toAdminDto()

        assertNotNull(result.stats)
        assertEquals(3, result.stats.gamesCount)
        assertEquals(12, result.stats.downloadedGamesCount)
    }

    @Test
    fun `toAdminDto should handle empty unmatchedPaths`() {
        val library = createTestLibrary(
            id = 1L,
            ignoredPaths = mutableListOf()
        )

        val result = library.toAdminDto()

        assertTrue(result.ignoredPaths?.isEmpty() ?: false)
    }

    @Test
    fun `toAdminDto should include all unmatchedPaths`() {
        val library = createTestLibrary(
            id = 1L,
            ignoredPaths = mutableListOf(
                createTestIgnoredPath("/path1"),
                createTestIgnoredPath("/path2"),
                createTestIgnoredPath("/path3")
            )
        )

        val result = library.toAdminDto()

        assertEquals(3, result.ignoredPaths?.size ?: 0)
        assertTrue(result.ignoredPaths?.map { it.path }?.contains("/path1") ?: false)
        assertTrue(result.ignoredPaths.map { it.path }.contains("/path2"))
        assertTrue(result.ignoredPaths.map { it.path }.contains("/path3"))
    }

    @Test
    fun `toAdminDto should map directory with null externalPath`() {
        val dir = createDirectoryMapping("/internal", null)
        val library = createTestLibrary(
            id = 1L,
            directories = mutableListOf(dir)
        )

        val result = library.toAdminDto()

        assertEquals(1, result.directories.size)
        assertEquals("/internal", result.directories[0].internalPath)
        assertEquals(null, result.directories[0].externalPath)
    }

    private fun createTestLibrary(
        id: Long,
        name: String = "Test Library",
        directories: MutableList<DirectoryMapping> = mutableListOf(),
        platforms: MutableList<Platform> = mutableListOf(),
        games: MutableList<Game> = mutableListOf(),
        ignoredPaths: MutableList<IgnoredPath> = mutableListOf()
    ): Library {
        return mockk<Library>(relaxed = true) {
            every { this@mockk.id } returns id
            every { this@mockk.name } returns name
            every { this@mockk.directories } returns directories
            every { this@mockk.platforms } returns platforms
            every { this@mockk.games } returns games
            every { this@mockk.ignoredPaths } returns ignoredPaths
        }
    }

    private fun createDirectoryMapping(
        internalPath: String,
        externalPath: String?
    ): DirectoryMapping {
        return mockk<DirectoryMapping>(relaxed = true) {
            every { this@mockk.internalPath } returns internalPath
            every { this@mockk.externalPath } returns externalPath
        }
    }

    private fun createTestIgnoredPath(path: String): IgnoredPath {
        val pluginSource = IgnoredPathPluginSource(mutableListOf())
        return IgnoredPath(id = 0L, path = path, source = pluginSource)
    }

    private fun createTestGame(id: Long?): Game {
        val metadata = GameMetadata(path = "/path/game")
        return mockk<Game>(relaxed = true) {
            every { this@mockk.id } returns id
            every { this@mockk.metadata } returns metadata
        }
    }
}

