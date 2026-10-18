package org.gameyfin.app.core.plugins.data

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginDataServiceTest {

    private lateinit var gameRepository: PluginGameDataRepository
    private lateinit var libraryRepository: PluginLibraryDataRepository
    private lateinit var collectionRepository: PluginCollectionDataRepository
    private lateinit var service: PluginDataService

    private val created = Instant.parse("2024-01-01T00:00:00Z")
    private val updated = Instant.parse("2024-02-01T00:00:00Z")

    private fun gameRow(id: Long, title: String?, libraryId: Long, path: String): Array<Any?> =
        arrayOf(id, title, libraryId, path, 1024L, null, created, updated)

    @BeforeEach
    fun setup() {
        gameRepository = mockk()
        libraryRepository = mockk()
        collectionRepository = mockk()
        service = PluginDataService(gameRepository, libraryRepository, collectionRepository)
    }

    @Test
    fun `getGames should map rows and platforms`() {
        every { gameRepository.findAllRows() } returns listOf(
            gameRow(1, "Game A", 10, "/games/a"),
            gameRow(2, null, 10, "/games/b.zip")
        )
        every { gameRepository.findAllPlatforms() } returns listOf(
            arrayOf(1L, Platform.PC_MICROSOFT_WINDOWS),
            arrayOf(1L, Platform.LINUX)
        )

        val games = service.getGames()

        assertEquals(2, games.size)
        val first = games[0]
        assertEquals(1, first.id)
        assertEquals("Game A", first.title)
        assertEquals(10, first.libraryId)
        assertEquals(Path.of("/games/a"), first.path)
        assertEquals(1024L, first.fileSize)
        assertEquals(listOf(Platform.PC_MICROSOFT_WINDOWS, Platform.LINUX), first.platforms)
        assertEquals(created, first.createdAt)
        assertEquals(updated, first.updatedAt)
        assertNull(games[1].title)
        assertTrue(games[1].platforms.isEmpty())
    }

    @Test
    fun `getGames by IDs should not query for empty IDs`() {
        assertTrue(service.getGames(emptyList()).isEmpty())
        verify(exactly = 0) { gameRepository.findRowsByIds(any()) }
    }

    @Test
    fun `getGame should return null for unknown games`() {
        every { gameRepository.findRowsByIds(setOf(5L)) } returns emptyList()
        every { gameRepository.findPlatformsByIds(setOf(5L)) } returns emptyList()

        assertNull(service.getGame(5))
    }

    @Test
    fun `getGamesOfLibrary should only load platforms of the games of the library`() {
        every { gameRepository.findRowsByLibraryId(10) } returns listOf(gameRow(3, "Game C", 10, "/games/c"))
        every { gameRepository.findPlatformsByIds(listOf(3L)) } returns listOf(arrayOf(3L, Platform.LINUX))

        val games = service.getGamesOfLibrary(10)

        assertEquals(listOf(3L), games.map { it.id })
        assertEquals(listOf(Platform.LINUX), games[0].platforms)
    }

    @Test
    fun `getLibraries should map directories and platforms`() {
        every { libraryRepository.findAllNames() } returns listOf(arrayOf(10L, "Library"), arrayOf(11L, "Empty"))
        every { libraryRepository.findAllDirectories() } returns listOf(arrayOf(10L, "/games"), arrayOf(10L, "/more"))
        every { libraryRepository.findAllPlatforms() } returns listOf(arrayOf(10L, Platform.LINUX))

        val libraries = service.getLibraries()

        assertEquals(listOf(Path.of("/games"), Path.of("/more")), libraries[0].directories)
        assertEquals(listOf(Platform.LINUX), libraries[0].platforms)
        assertTrue(libraries[1].directories.isEmpty())
        assertEquals("Empty", service.getLibrary(11)?.name)
        assertNull(service.getLibrary(12))
    }

    @Test
    fun `getCollections should map the sorted game IDs`() {
        every { collectionRepository.findAllRows() } returns listOf(arrayOf(1L, "Favorites", null))
        every { collectionRepository.findAllGameIds() } returns listOf(arrayOf(1L, 7L), arrayOf(1L, 3L))

        val collection = service.getCollection(1)

        assertEquals("Favorites", collection?.name)
        assertNull(collection?.description)
        assertEquals(listOf(3L, 7L), collection?.gameIds)
    }
}
