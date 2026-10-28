package org.gameyfin.app.collections

import io.mockk.*
import org.gameyfin.app.collections.dto.*
import org.gameyfin.app.collections.entities.Collection
import org.gameyfin.app.collections.repositories.CollectionRepository
import org.gameyfin.app.core.sync.EntityEventType
import org.gameyfin.app.core.sync.SYNC_SAFETY_WINDOW
import org.gameyfin.app.games.GameService
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.entities.GameMetadata
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.repository.findByIdOrNull
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.test.*

class CollectionServiceTest {

    private lateinit var repository: CollectionRepository
    private lateinit var gameService: GameService
    private lateinit var service: CollectionService

    @BeforeEach
    fun setup() {
        repository = mockk()
        gameService = mockk()
        service = CollectionService(repository, gameService)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `create should persist new collection`() {
        val dto = CollectionCreateDto(name = "RPGs", description = "Role Playing Games", gameIds = listOf())
        val entitySlot = slot<Collection>()
        every { repository.findByName("RPGs") } returns null
        every { repository.save(capture(entitySlot)) } answers {
            entitySlot.captured.apply {
                id = 1L; createdAt = Instant.now(); updatedAt = createdAt
            }
        }

        service.create(dto)

        verify { repository.save(any()) }
    }

    @Test
    fun `create should reject duplicate name`() {
        val dto = CollectionCreateDto(name = "RPGs")
        every { repository.findByName("RPGs") } returns Collection(name = "RPGs")
        assertFailsWith<IllegalArgumentException> { service.create(dto) }
    }

    @Test
    fun `update should modify name and description`() {
        val existing =
            Collection(name = "RPGs").apply { id = 1L; createdAt = Instant.now(); updatedAt = createdAt }
        every { repository.findById(1L) } returns Optional.of(existing)
        every { repository.findByName("New Name") } returns null
        every { repository.saveAndFlush(existing) } returns existing
        stubGameIdsFrom(existing)
        every { repository.findByIdOrNull(1L) } returns existing

        val dto = CollectionUpdateDto(id = 1L, name = "New Name", description = "Updated")
        val result = service.update(dto)
        assertEquals("New Name", result.name)
        assertEquals("Updated", result.description)
    }

    @Test
    fun `addGame should associate game`() {
        val existing =
            Collection(name = "Action").apply { id = 1L; createdAt = Instant.now(); updatedAt = createdAt }
        val game = Game(
            library = mockk(relaxed = true),
            metadata = GameMetadata(path = "test")
        )
        game.id = 10L
        every { repository.findByIdOrNull(1L) } returns existing
        every { gameService.getById(10L) } returns game
        every { repository.saveAndFlush(existing) } returns existing
        stubGameIdsFrom(existing)

        val result = service.addGame(1L, 10L)
        assertEquals(1, result.gameIds.size)
        assertEquals(10L, result.gameIds.first())
    }

    @Test
    fun `sync without timestamp should return all collections with game ids`() {
        val c1 =
            Collection(name = "Indie").apply { id = 1L; createdAt = Instant.now(); updatedAt = createdAt }
        val c2 = Collection(name = "AAA").apply { id = 2L; createdAt = Instant.now(); updatedAt = createdAt }
        every { repository.findAll() } returns listOf(c1, c2)
        every { repository.findGameIdsByCollectionIds(listOf(1L, 2L)) } returns listOf(
            arrayOf<Any>(1L, 12L), arrayOf<Any>(1L, 11L), arrayOf<Any>(2L, 13L)
        )

        val result = service.sync(null)

        assertTrue(result.full)
        assertTrue(result.allIds.isEmpty())
        assertEquals(listOf("Indie", "AAA"), result.collections.map { it.name })
        assertEquals(listOf(11L, 12L), result.collections[0].gameIds)
        assertEquals(listOf(13L), result.collections[1].gameIds)
    }

    @Test
    fun `sync with timestamp should return changed collections and all ids`() {
        val since = Instant.parse("2024-01-01T12:00:00Z")
        val c1 = Collection(name = "Indie").apply { id = 1L; createdAt = Instant.now(); updatedAt = createdAt }
        every { repository.findAllByUpdatedAtAfter(since.minus(SYNC_SAFETY_WINDOW)) } returns listOf(c1)
        every { repository.findAllIds() } returns listOf(1L, 2L)
        every { repository.findGameIdsByCollectionIds(listOf(1L)) } returns emptyList()

        val result = service.sync(since)

        assertFalse(result.full)
        assertEquals(listOf(1L, 2L), result.allIds)
        assertEquals(1, result.collections.size)
        assertEquals(emptyList(), result.collections[0].gameIds)
        verify(exactly = 0) { repository.findAll() }
    }

    @Test
    fun `sync with timestamp should not query game ids if nothing changed`() {
        val since = Instant.now()
        every { repository.findAllByUpdatedAtAfter(any()) } returns emptyList()
        every { repository.findAllIds() } returns listOf(1L)

        val result = service.sync(since)

        assertTrue(result.collections.isEmpty())
        verify(exactly = 0) { repository.findGameIdsByCollectionIds(any()) }
    }

    @Test
    fun `getDtoById should return null when not found`() {
        every { repository.findByIdOrNull(42L) } returns null
        assertNull(service.getDtoById(42L))
    }

    @Test
    fun `getById should throw when not found`() {
        every { repository.findByIdOrNull(42L) } returns null
        assertFailsWith<IllegalArgumentException> { service.getById(42L) }
    }

    @Test
    fun `create should attach provided games`() {
        val g1 = Game(library = mockk(relaxed = true), metadata = GameMetadata(path = "p1")).apply { id = 11L }
        val g2 = Game(library = mockk(relaxed = true), metadata = GameMetadata(path = "p2")).apply { id = 12L }
        val dto = CollectionCreateDto(name = "Favorites", gameIds = listOf(11L, 12L, 11L))
        val entitySlot = slot<Collection>()
        every { repository.findByName("Favorites") } returns null
        every { gameService.getById(11L) } returns g1
        every { gameService.getById(12L) } returns g2
        every { repository.save(capture(entitySlot)) } answers {
            entitySlot.captured.apply { id = 5L; createdAt = Instant.now(); updatedAt = createdAt }
        }
        service.create(dto)
        verify { repository.save(any()) }
    }

    @Test
    fun `update should reject duplicate name`() {
        val existing =
            Collection(name = "Old").apply { id = 3L; createdAt = Instant.now(); updatedAt = createdAt }
        every { repository.findById(3L) } returns Optional.of(existing)
        every { repository.findByName("Old") } returns existing
        every { repository.findByName("New") } returns Collection(name = "New")
        assertFailsWith<IllegalArgumentException> { service.update(CollectionUpdateDto(id = 3L, name = "New")) }
    }

    @Test
    fun `update should replace games set`() {
        val existing =
            Collection(name = "Mix").apply { id = 4L; createdAt = Instant.now(); updatedAt = createdAt }
        val g1 = Game(library = mockk(relaxed = true), metadata = GameMetadata(path = "a")).apply { id = 21L }
        val g2 = Game(library = mockk(relaxed = true), metadata = GameMetadata(path = "b")).apply { id = 22L }
        // pre-populate with one game to ensure replacement
        val old = Game(library = mockk(relaxed = true), metadata = GameMetadata(path = "old")).apply { id = 99L }
        existing.addGame(old)
        every { repository.findById(4L) } returns Optional.of(existing)
        every { gameService.getById(21L) } returns g1
        every { gameService.getById(22L) } returns g2
        every { repository.saveAndFlush(existing) } returns existing
        stubGameIdsFrom(existing)
        every { repository.findByIdOrNull(4L) } returns existing
        val dto = CollectionUpdateDto(id = 4L, gameIds = listOf(21L, 22L))
        val result = service.update(dto)
        assertEquals(listOf(21L, 22L), result.gameIds)
    }

    @Test
    fun `removeGame should detach association`() {
        val existing =
            Collection(name = "Arcade").apply { id = 6L; createdAt = Instant.now(); updatedAt = createdAt }
        val game = Game(library = mockk(relaxed = true), metadata = GameMetadata(path = "x")).apply { id = 77L }
        existing.addGame(game)
        every { repository.findByIdOrNull(6L) } returns existing
        every { gameService.getById(77L) } returns game
        every { repository.saveAndFlush(existing) } returns existing
        stubGameIdsFrom(existing)

        val result = service.removeGame(6L, 77L)

        assertEquals(0, result.gameIds.size)
    }

    @Test
    fun `delete should delegate to repository`() {
        every { repository.deleteById(8L) } just Runs
        service.delete(8L)
        verify { repository.deleteById(8L) }
    }

    @Test
    fun `companion events emit and subscribe`() {
        // Integration tests may have emitted other events into the shared broadcaster.
        val flux = CollectionService.subscribe().flatMapIterable { it }
            .filter { it.id == -2001L || it.id == -2002L }.take(2).collectList()
        val now = Instant.now()
        val dto = CollectionDto(1L, now, now, "n", null, emptyList(), CollectionMetadataDto(true, 1, emptyMap()))
        CollectionService.emit(CollectionEvent(EntityEventType.CREATED, -2001L, collection = dto))
        CollectionService.emit(CollectionEvent(EntityEventType.DELETED, -2002L))
        val batch = flux.block(Duration.ofSeconds(2))
        assertEquals(2, batch?.size)
    }

    private fun stubGameIdsFrom(collection: Collection) {
        every { repository.findGameIdsByCollectionIds(listOf(collection.id!!)) } answers {
            collection.games.map { arrayOf(collection.id!!, it.id!!) }
        }
    }
}
