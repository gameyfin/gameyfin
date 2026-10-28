package org.gameyfin.app.api.v1

import io.mockk.*
import org.gameyfin.app.api.security.ApiAccess
import org.gameyfin.app.api.security.ApiScope
import org.gameyfin.app.api.v1.dto.*
import org.gameyfin.app.api.v1.service.CatalogServiceV1
import org.gameyfin.app.collections.CollectionService
import org.gameyfin.app.collections.dto.CollectionUpdateDto
import org.gameyfin.app.collections.entities.Collection
import org.gameyfin.app.collections.repositories.CollectionRepository
import org.gameyfin.app.games.GameService
import org.gameyfin.app.games.dto.GameUpdateDto
import org.gameyfin.app.games.entities.GameFieldApiSource
import org.gameyfin.app.games.entities.GameFieldMetadata
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.libraries.LibraryRepository
import org.gameyfin.app.libraries.LibraryService
import org.gameyfin.app.libraries.dto.LibraryAdminDto
import org.gameyfin.app.libraries.dto.LibraryUpdateDto
import org.gameyfin.app.libraries.entities.DirectoryMapping
import org.gameyfin.pluginapi.gamemetadata.Genre
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.domain.Specification
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.access.AccessDeniedException
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CatalogServiceV1Test {
    private val games = mockk<GameRepository>(relaxed = true)
    private val libraries = mockk<LibraryRepository>(relaxed = true)
    private val collections = mockk<CollectionRepository>(relaxed = true)
    private val gameService = mockk<GameService>(relaxed = true)
    private val libraryService = mockk<LibraryService>(relaxed = true)
    private val collectionService = mockk<CollectionService>(relaxed = true)
    private val access = mockk<ApiAccess>(relaxed = true)
    private val mapper = JsonMapper.builder().findAndAddModules().build()
    private val service = CatalogServiceV1(
        games, libraries, collections, gameService, libraryService, collectionService, mapper, access
    )

    private val page = PageRequest.of(0, 10)

    @Test
    fun `games reject inconsistent or invalid filters`() {
        assertFailsWith<IllegalArgumentException> { service.games(page, null, null, null, null, "igdb", null) }
        assertFailsWith<IllegalArgumentException> { service.games(page, null, null, null, null, null, "1") }
        assertFailsWith<IllegalArgumentException> { service.games(page, "x".repeat(256), null, null, null, null, null) }
        assertFailsWith<IllegalArgumentException> { service.games(page, null, "abc", null, null, null, null) }
        assertFailsWith<IllegalArgumentException> { service.games(page, null, null, "0", null, null, null) }
        assertFailsWith<IllegalArgumentException> { service.games(page, null, null, null, "NOPE", null, null) }
    }

    @Test
    fun `games build a specification for every filter`() {
        val game = testGame()
        val spec = slot<Specification<org.gameyfin.app.games.entities.Game>>()
        every { games.findAll(capture(spec), page) } returns PageImpl(listOf(game), page, 1)

        val result = service.games(page, "50%_off", "1", "2", Platform.entries.first().name, "igdb", "42")

        assertEquals(listOf("10"), result.items.map { it.id })
        val root = mockk<jakarta.persistence.criteria.Root<org.gameyfin.app.games.entities.Game>>(relaxed = true)
        val query = mockk<jakarta.persistence.criteria.CriteriaQuery<*>>(relaxed = true)
        val cb = mockk<jakarta.persistence.criteria.CriteriaBuilder>(relaxed = true)
        spec.captured.toPredicate(root, query, cb)
        verify { query.distinct(true) }
        verify { cb.like(any(), "%50\\%\\_off%", '\\') }
        verify(atLeast = 4) { cb.equal(any(), any<Any>()) }

        service.games(page, null, null, null, null, null, null)
        spec.captured.toPredicate(root, query, cb)
    }

    @Test
    fun `entities are looked up by valid ids and missing ones are reported`() {
        every { games.findById(10) } returns Optional.of(testGame())
        every { games.findById(11) } returns Optional.empty()
        every { libraries.findById(1) } returns Optional.of(testLibrary())
        every { libraries.findById(2) } returns Optional.empty()
        every { collections.findById(3) } returns Optional.of(Collection(id = 3, name = "C"))
        every { collections.findById(4) } returns Optional.empty()

        assertEquals("10", service.game("10").id)
        assertEquals("1", service.library("1").id)
        assertEquals("3", service.collection("3").id)
        assertFailsWith<NoSuchElementException> { service.gameEntity("11") }
        assertFailsWith<NoSuchElementException> { service.libraryEntity("2") }
        assertFailsWith<NoSuchElementException> { service.collectionEntity("4") }
        assertFailsWith<IllegalArgumentException> { service.game("x") }
    }

    @Test
    fun `lists map to public pages and configuration exposes directories`() {
        every { libraries.findAll(page) } returns PageImpl(listOf(testLibrary()), page, 1)
        every { collections.findAll(page) } returns PageImpl(listOf(Collection(id = 3, name = "C")), page, 1)
        val library = testLibrary().apply {
            directories = mutableListOf(DirectoryMapping(internalPath = "/in", externalPath = "/out"))
            platforms = mutableListOf(Platform.entries.first())
        }
        every { libraries.findById(1) } returns Optional.of(library)

        assertEquals(1, service.libraries(page).totalElements)
        assertEquals(1, service.collections(page).totalElements)
        assertEquals(
            LibraryConfigurationV1(listOf(DirectoryV1("/in", "/out")), listOf(Platform.entries.first().name)),
            service.configuration("1")
        )
    }

    @Test
    fun `game patches validate fields before touching the game`() {
        assertFailsWith<IllegalArgumentException> { service.patchGame("10", mapOf("id" to "1")) }
        assertFailsWith<IllegalArgumentException> { service.patchGame("10", mapOf("title" to null)) }
        assertFailsWith<IllegalArgumentException> { service.patchGame("10", mapOf("title" to " ")) }
        assertFailsWith<IllegalArgumentException> { service.patchGame("10", mapOf("release" to "not-a-date")) }
        verify(exactly = 0) { gameService.edit(any(), any()) }
    }

    @Test
    fun `game patches apply values clear nulls and record api attribution`() {
        val game = testGame().apply {
            summary = "old"; comment = "old"; release = java.time.Instant.EPOCH
            metadata.fields["title"] = GameFieldMetadata(source = GameFieldApiSource("old", "old"))
        }
        every { games.findById(10) } returns Optional.of(game)
        every { gameService.editingSourceFactory() } returns { GameFieldApiSource("cred", "Key") }
        val update = slot<GameUpdateDto>()
        every { gameService.edit(capture(update), any()) } just runs

        val result = service.patchGame(
            "10", mapOf(
                "title" to "New", "summary" to null, "comment" to null, "release" to null,
                "genres" to listOf(Genre.entries.first().name), "platforms" to listOf(Platform.entries.first().name)
            )
        )

        assertEquals("New", update.captured.title)
        assertEquals(listOf(Genre.entries.first()), update.captured.genres)
        assertEquals(setOf(Platform.entries.first()), update.captured.platforms)
        assertNull(game.summary)
        assertNull(game.comment)
        assertNull(game.release)
        assertNull(result.release)
        assertEquals(setOf("title", "summary", "comment", "release", "genres", "platforms"), game.metadata.fields.keys)
        assertEquals("Key", (game.metadata.fields.getValue("title").source as GameFieldApiSource).credentialName)
        verify { games.flush() }
    }

    @Test
    fun `release dates are converted from json`() {
        every { games.findById(10) } returns Optional.of(testGame())
        every { gameService.editingSourceFactory() } returns { GameFieldApiSource("c", "n") }
        val update = slot<GameUpdateDto>()
        every { gameService.edit(capture(update), any()) } just runs

        service.patchGame("10", mapOf("release" to "2020-01-02"))

        assertEquals(LocalDate.of(2020, 1, 2), update.captured.release)
    }

    @Test
    fun `media replacement maps selections and clears missing artwork`() {
        val game = testGame()
        every { games.findById(10) } returns Optional.of(game)
        val update = slot<GameUpdateDto>()
        val uploads = mapOf("file" to MockMultipartFile("file", byteArrayOf(1)))
        every { gameService.edit(capture(update), uploads) } just runs

        service.replaceMedia(
            "10", GameMediaV1(
                cover = ImageSelectionV1(id = "5"), images = listOf(ImageSelectionV1(uploadKey = "file")),
                videoUrls = listOf("https://v.test"), mediaOrder = listOf("image", "video")
            ), uploads
        )

        assertEquals(5L, update.captured.cover!!.id)
        assertNull(update.captured.header)
        assertEquals("file", update.captured.images!!.single().uploadKey)
        assertEquals(listOf("https://v.test"), update.captured.videoUrls)
        verify { gameService.clearArtwork(10, false, true) }
    }

    @Test
    fun `libraries are created from validated input`() {
        val created = slot<LibraryAdminDto>()
        every { libraryService.createReturningId(capture(created), false) } returns 1
        every { libraries.findById(1) } returns Optional.of(testLibrary())

        val result = service.createLibrary(
            CreateLibraryV1("Library", listOf(DirectoryV1("/a", "/x")), listOf(Platform.entries.first().name))
        )

        assertEquals("1", result.id)
        assertEquals("/a", created.captured.directories.single().internalPath)
        assertEquals(listOf(Platform.entries.first()), created.captured.platforms)
        listOf(
            CreateLibraryV1(" ", listOf(DirectoryV1("/a"))),
            CreateLibraryV1("x".repeat(256), listOf(DirectoryV1("/a"))),
            CreateLibraryV1("L", emptyList()),
            CreateLibraryV1("L", listOf(DirectoryV1(" "))),
            CreateLibraryV1("L", listOf(DirectoryV1("/a"), DirectoryV1("/a")))
        ).forEach { assertFailsWith<IllegalArgumentException> { service.createLibrary(it) } }
    }

    @Test
    fun `library patches require delete permission when removing directories`() {
        val library = testLibrary().apply {
            directories =
                mutableListOf(DirectoryMapping(internalPath = "/keep"), DirectoryMapping(internalPath = "/drop"))
        }
        every { libraries.findById(1) } returns Optional.of(library)
        val update = slot<LibraryUpdateDto>()
        every { libraryService.update(capture(update)) } just runs

        service.patchLibrary("1", mapOf("directories" to listOf(mapOf("internalPath" to "/keep"))))

        verify { access.require(ApiScope.GAMES_DELETE) }
        assertEquals(listOf("/keep"), update.captured.directories!!.map { it.internalPath })

        clearMocks(access, answers = false)
        service.patchLibrary(
            "1", mapOf(
                "name" to "Renamed",
                "directories" to listOf(
                    mapOf("internalPath" to "/keep"),
                    mapOf("internalPath" to "/drop"),
                    mapOf("internalPath" to "/new")
                ),
                "platforms" to listOf(Platform.entries.first().name)
            )
        )
        verify(exactly = 0) { access.require(any()) }
        assertEquals("Renamed", update.captured.name)
        assertEquals(listOf(Platform.entries.first()), update.captured.platforms)
    }

    @Test
    fun `library patch is denied when delete permission is missing`() {
        val library = testLibrary().apply { directories = mutableListOf(DirectoryMapping(internalPath = "/drop")) }
        every { libraries.findById(1) } returns Optional.of(library)
        every { access.require(ApiScope.GAMES_DELETE) } throws AccessDeniedException("denied")

        assertFailsWith<AccessDeniedException> {
            service.patchLibrary("1", mapOf("directories" to listOf(mapOf("internalPath" to "/other"))))
        }
        verify(exactly = 0) { libraryService.update(any<LibraryUpdateDto>()) }
        assertFailsWith<IllegalArgumentException> { service.patchLibrary("1", mapOf("unknown" to 1)) }
        assertFailsWith<IllegalArgumentException> { service.patchLibrary("1", mapOf("name" to null)) }
    }

    @Test
    fun `collections are created and patched`() {
        every { collectionService.createReturningId(any()) } returns 3
        val collection = Collection(id = 3, name = "Old", description = "desc")
        every { collections.findById(3) } returns Optional.of(collection)
        val update = slot<CollectionUpdateDto>()
        every { collectionService.update(capture(update)) } returns mockk(relaxed = true)

        assertEquals("3", service.createCollection(CreateCollectionV1("New", "d")).id)
        assertFailsWith<IllegalArgumentException> { service.createCollection(CreateCollectionV1(" ")) }

        service.patchCollection("3", mapOf("name" to "Old", "description" to null))
        assertNull(update.captured.name)
        assertNull(collection.description)

        service.patchCollection("3", mapOf("name" to "Renamed"))
        assertEquals("Renamed", update.captured.name)
        assertFailsWith<IllegalArgumentException> { service.patchCollection("3", mapOf("name" to null)) }
    }

    @Test
    fun `membership changes are idempotent`() {
        val member = testGame(10)
        val other = testGame(11)
        val collection = Collection(id = 3, name = "C", games = mutableSetOf(member))
        every { collections.findById(3) } returns Optional.of(collection)
        every { games.findById(10) } returns Optional.of(member)
        every { games.findById(11) } returns Optional.of(other)

        service.membership("3", "10", true)
        service.membership("3", "11", false)
        verify(exactly = 0) { collectionService.addGame(any(), any()) }
        verify(exactly = 0) { collectionService.removeGame(any(), any()) }

        service.membership("3", "11", true)
        service.membership("3", "10", false)
        verify { collectionService.addGame(3, 11) }
        verify { collectionService.removeGame(3, 10) }
    }
}
