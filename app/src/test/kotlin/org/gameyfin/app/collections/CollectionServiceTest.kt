package org.gameyfin.app.collections

import io.mockk.*
import org.gameyfin.app.collections.dto.*
import org.gameyfin.app.collections.entities.Collection
import org.gameyfin.app.collections.repositories.CollectionRepository
import org.gameyfin.app.games.GameService
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.entities.GameMetadata
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.repository.findByIdOrNull
import reactor.core.publisher.Flux
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

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
        every { repository.save(existing) } returns existing
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
        every { gameService.update(game) } returns game.apply { collections += existing }
        every { repository.save(existing) } returns existing

        val result = service.addGame(1L, 10L)
        assertEquals(1, result.gameIds?.size)
        assertEquals(10L, result.gameIds?.first())
    }

    @Test
    fun `getAll should return mapped dtos`() {
        val c1 =
            Collection(name = "Indie").apply { id = 1L; createdAt = Instant.now(); updatedAt = createdAt }
        val c2 = Collection(name = "AAA").apply { id = 2L; createdAt = Instant.now(); updatedAt = createdAt }
        every { repository.findAll() } returns listOf(c1, c2)
        val result = service.getAll()
        assertEquals(2, result.size)
        assertEquals("Indie", result[0].name)
        assertEquals("AAA", result[1].name)
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
        val result = service.create(dto)
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
        every { repository.save(existing) } returns existing
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
        every { gameService.update(game) } returns game.apply { collections -= existing }
        every { repository.save(existing) } returns existing

        val result = service.removeGame(6L, 77L)

        assertEquals(0, result.gameIds?.size ?: 0)
    }

    @Test
    fun `delete should delegate to repository`() {
        every { repository.deleteById(8L) } just Runs
        service.delete(8L)
        verify { repository.deleteById(8L) }
    }

    @Test
    fun `companion events emit and subscribe`() {
        // subscribe and capture first buffered batch
        val flux: Flux<List<CollectionUserEvent>> = CollectionService.subscribeUser().take(1)
        val now = Instant.now()
        val userDto =
            CollectionUserDto(1L, now, now, "n", null, emptyList(), CollectionMetadataDto(true, 1, emptyMap()))
        CollectionService.emitUser(CollectionUserEvent.Created(userDto))
        val batch = flux.blockFirst(Duration.ofSeconds(1))
        assertEquals(1, batch?.size)

        val adminFlux: Flux<List<CollectionAdminEvent>> = CollectionService.subscribeAdmin().take(1)
        CollectionService.emitAdmin(CollectionAdminEvent.Deleted(2L))
        val adminBatch = adminFlux.blockFirst(Duration.ofSeconds(1))
        assertEquals(1, adminBatch?.size)
    }
}
