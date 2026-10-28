package org.gameyfin.app.api.v1

import io.mockk.*
import org.gameyfin.app.api.security.ApiScope
import org.gameyfin.app.core.tasks.DeletionTaskService
import org.gameyfin.app.games.GameService
import org.gameyfin.app.libraries.LibraryCoreService
import org.gameyfin.app.libraries.LibraryScanService
import org.gameyfin.app.libraries.enums.ScanType
import org.gameyfin.app.platforms.PlatformService
import org.gameyfin.pluginapi.gamemetadata.Genre
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.mock.web.MockMultipartFile
import org.springframework.mock.web.MockMultipartHttpServletRequest
import kotlin.test.*
import org.gameyfin.app.api.v1.dto.*

class CatalogControllerV1Test {
    private val access = allowingAccess()
    private val catalog = mockk<CatalogServiceV1>()
    private val games = mockk<GameService>(relaxed = true)
    private val libraries = mockk<LibraryCoreService>(relaxed = true)
    private val scans = mockk<LibraryScanService>(relaxed = true)
    private val deletions = mockk<DeletionTaskService>()
    private val platforms = mockk<PlatformService>()
    private val controller = CatalogControllerV1(access, catalog, games, libraries, scans, deletions, platforms)

    private val game = testGame().publicGame()
    private val library = LibraryV1("1", "Library", emptyList(), null, null)
    private val collection = CollectionV1("2", "Collection", null, null, null)
    private val emptyGames = PageV1<GameV1>(emptyList(), 0, 50, 0)

    @Test
    fun `games are listed and fetched with games read`() {
        every { catalog.games(any(), any(), any(), any(), any(), any(), any()) } returns emptyGames
        every { catalog.game("10") } returns game

        assertEquals(emptyGames, controller.games(0, 50, "title,desc", "q", "1", "2", "PC", "igdb", "42"))
        assertEquals(game, controller.game("10"))

        verify {
            catalog.games(
                PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "title").and(Sort.by("id"))),
                "q", "1", "2", "PC", "igdb", "42"
            )
        }
        verify(exactly = 2) { access.require(ApiScope.GAMES_READ) }
    }

    @Test
    fun `game edits delegate to the catalog`() {
        every { catalog.patchGame("10", any()) } returns game
        assertEquals(game, controller.patchGame("10", mapOf("title" to "New")))
        verify { access.require(ApiScope.GAMES_WRITE) }
    }

    @Test
    fun `media replacement forwards uploads except the metadata part`() {
        val metadata = GameMediaV1()
        val request = MockMultipartHttpServletRequest().apply {
            addFile(MockMultipartFile("metadata", "{}".toByteArray()))
            addFile(MockMultipartFile("cover", "cover.png", "image/png", byteArrayOf(1)))
        }
        val files = slot<Map<String, org.springframework.web.multipart.MultipartFile>>()
        every { catalog.replaceMedia("10", metadata, capture(files)) } returns game

        assertEquals(game, controller.media("10", metadata, request))
        assertEquals(setOf("cover"), files.captured.keys)

        request.addFile(MockMultipartFile("cover", "second.png", "image/png", byteArrayOf(2)))
        assertFailsWith<IllegalArgumentException> { controller.media("10", metadata, request) }
    }

    @Test
    fun `game deletion removes the catalog entry`() {
        every { catalog.game("10") } returns game

        assertEquals(204, controller.deleteGame("10").statusCode.value())

        verifyOrder {
            access.require(ApiScope.GAMES_DELETE)
            libraries.deleteGameFromLibrary(10)
            games.delete(10)
        }
    }

    @Test
    fun `libraries can be listed fetched configured and patched`() {
        val page = PageV1(listOf(library), 0, 50, 1)
        val configuration = LibraryConfigurationV1(listOf(DirectoryV1("/games")), emptyList())
        every { catalog.libraries(any()) } returns page
        every { catalog.library("1") } returns library
        every { catalog.configuration("1") } returns configuration
        every { catalog.patchLibrary("1", any()) } returns library

        assertEquals(page, controller.libraries(0, 50, "name"))
        assertEquals(library, controller.library("1"))
        assertEquals(configuration, controller.configuration("1"))
        assertEquals(library, controller.patchLibrary("1", mapOf("name" to "Renamed")))
        verify { access.require(ApiScope.LIBRARIES_WRITE) }
        assertFailsWith<IllegalArgumentException> { controller.libraries(0, 50, "title") }
    }

    @Test
    fun `library creation optionally queues a quick scan`() {
        every { catalog.createLibrary(any()) } returns library
        val input = CreateLibraryV1("Library", listOf(DirectoryV1("/games")))

        val created = controller.createLibrary(input)
        assertEquals(201, created.statusCode.value())
        assertEquals("/v1/libraries/1", created.headers.location.toString())
        verify(exactly = 0) { scans.triggerScan(any(), any(), any()) }
        verify(exactly = 0) { access.require(ApiScope.LIBRARIES_SCAN) }

        controller.createLibrary(input.copy(scanAfterCreation = true))
        verify { access.require(ApiScope.LIBRARIES_SCAN) }
        verify { scans.triggerScan(ScanType.QUICK, listOf(1L), any()) }
    }

    @Test
    fun `library deletion and scans are accepted`() {
        every { catalog.library("1") } returns library
        every { deletions.deleteLibrary(1) } returns taskRun("run-lib")

        val deleted = controller.deleteLibrary("1")
        assertEquals(202, deleted.statusCode.value())
        assertEquals("/v1/task-runs/run-lib", deleted.headers.location.toString())
        assertEquals("run-lib", deleted.body!!.id)

        val scan = controller.scan("1", ScanV1("FULL"))
        assertEquals(202, scan.statusCode.value())
        assertEquals(AcceptedScanV1("1", "FULL"), scan.body)
        verify { scans.triggerScan(ScanType.FULL, listOf(1L), any()) }

        assertFailsWith<IllegalArgumentException> { controller.scan("1", ScanV1("PARTIAL")) }
    }

    @Test
    fun `collections can be listed created patched and deleted`() {
        val page = PageV1(listOf(collection), 0, 50, 1)
        every { catalog.collections(any()) } returns page
        every { catalog.collection("2") } returns collection
        every { catalog.createCollection(any()) } returns collection
        every { catalog.patchCollection("2", any()) } returns collection
        every { deletions.deleteCollection(2) } returns taskRun("run-col")

        assertEquals(page, controller.collections(0, 50, "id"))
        assertEquals(collection, controller.collection("2"))
        val created = controller.createCollection(CreateCollectionV1("Collection"))
        assertEquals(201, created.statusCode.value())
        assertEquals("/v1/collections/2", created.headers.location.toString())
        assertEquals(collection, controller.patchCollection("2", mapOf("name" to "Renamed")))
        val deleted = controller.deleteCollection("2")
        assertEquals(202, deleted.statusCode.value())
        assertEquals("/v1/task-runs/run-col", deleted.headers.location.toString())
    }

    @Test
    fun `collection games and membership delegate to the catalog`() {
        every { catalog.collection("2") } returns collection
        every { catalog.games(any(), null, null, "2", null, null, null) } returns emptyGames
        every { catalog.membership(any(), any(), any()) } just runs

        assertEquals(emptyGames, controller.collectionGames("2", 0, 50))
        assertEquals(204, controller.addGame("2", "10").statusCode.value())
        assertEquals(204, controller.removeGame("2", "10").statusCode.value())

        verify { access.require(ApiScope.COLLECTIONS_READ) }
        verify { catalog.membership("2", "10", true) }
        verify { catalog.membership("2", "10", false) }
    }

    @Test
    fun `platforms and metadata options are listed`() {
        val available = Platform.entries.take(2).toSet()
        every { platforms.availablePlatforms } returns available

        val result = controller.platforms()
        assertEquals(available.map { it.name }.sorted(), result.map { it.code })
        assertEquals(available.first { it.name == result.first().code }.displayName, result.first().name)

        val options = controller.options()
        assertEquals(setOf("genres", "themes", "features", "perspectives"), options.keys)
        assertEquals(Genre.entries.size, options.getValue("genres").size)
    }
}
