package org.gameyfin.app.core.sync

import io.mockk.*
import org.gameyfin.app.collections.CollectionService
import org.gameyfin.app.collections.dto.CollectionEvent
import org.gameyfin.app.collections.dto.CollectionField
import org.gameyfin.app.collections.entities.Collection
import org.gameyfin.app.collections.repositories.CollectionRepository
import org.gameyfin.app.games.GameService
import org.gameyfin.app.games.dto.*
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.extensions.toPatchDto
import org.gameyfin.app.games.extensions.toSummaryDto
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.libraries.LibraryRepository
import org.gameyfin.app.libraries.LibraryService
import org.gameyfin.app.libraries.dto.LibraryEvent
import org.gameyfin.app.libraries.dto.LibraryField
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.libraries.extensions.toPatchDto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EntityChangeProcessorTest {

    private lateinit var gameRepository: GameRepository
    private lateinit var libraryRepository: LibraryRepository
    private lateinit var collectionRepository: CollectionRepository
    private lateinit var processor: EntityChangeProcessor

    private val gameUserEvents = mutableListOf<GameEvent>()
    private val gameAdminEvents = mutableListOf<GameEvent>()
    private val libraryUserEvents = mutableListOf<LibraryEvent>()
    private val libraryAdminEvents = mutableListOf<LibraryEvent>()
    private val collectionEvents = mutableListOf<CollectionEvent>()

    @BeforeEach
    fun setup() {
        gameRepository = mockk(relaxed = true)
        libraryRepository = mockk(relaxed = true)
        collectionRepository = mockk(relaxed = true)
        val transactionManager = mockk<PlatformTransactionManager>(relaxed = true)
        processor = EntityChangeProcessor(gameRepository, libraryRepository, collectionRepository, transactionManager)

        every { gameRepository.findAllById(any()) } returns emptyList()
        every { libraryRepository.findAllById(any()) } returns emptyList()
        every { collectionRepository.findAllById(any()) } returns emptyList()

        mockkStatic("org.gameyfin.app.games.extensions.GameExtensionsKt")
        mockkStatic("org.gameyfin.app.libraries.extensions.LibraryExtensionsKt")

        mockkObject(GameService.Companion, LibraryService.Companion, CollectionService.Companion)
        every { GameService.emitUser(capture(gameUserEvents)) } just Runs
        every { GameService.emitAdmin(capture(gameAdminEvents)) } just Runs
        every { LibraryService.emitUser(capture(libraryUserEvents)) } just Runs
        every { LibraryService.emitAdmin(capture(libraryAdminEvents)) } just Runs
        every { CollectionService.emit(capture(collectionEvents)) } just Runs
    }

    @AfterEach
    fun tearDown() {
        processor.shutdown()
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `deleted game should emit ID-only events`() {
        val changes = EntityChangeSet().apply { game(1L).deleted = true }

        processor.process(changes)

        assertEquals(listOf(GameEvent(EntityEventType.DELETED, 1L)), gameUserEvents)
        assertEquals(listOf(GameEvent(EntityEventType.DELETED, 1L)), gameAdminEvents)
        verify(exactly = 0) { gameRepository.bumpUpdatedAt(any(), any()) }
    }

    @Test
    fun `created game should emit summaries`() {
        val game = mockGame(1L)
        val userSummary = mockk<GameSummaryDto>()
        val adminSummary = mockk<GameSummaryDto>()
        every { gameRepository.findAllById(listOf(1L)) } returns listOf(game)
        every { game.toSummaryDto(false) } returns userSummary
        every { game.toSummaryDto(true) } returns adminSummary

        processor.process(EntityChangeSet().apply { game(1L).created = true })

        assertEquals(listOf(GameEvent(EntityEventType.CREATED, 1L, summary = userSummary)), gameUserEvents)
        assertEquals(listOf(GameEvent(EntityEventType.CREATED, 1L, summary = adminSummary)), gameAdminEvents)
    }

    @Test
    fun `updated game should emit patches and skip invisible ones`() {
        val game = mockGame(1L)
        val adminPatch = GamePatchDto(Instant.now(), listOf(GameField.PATH), GamePatchValuesDto(path = "/new"))
        every { gameRepository.findAllById(listOf(1L)) } returns listOf(game)
        every { game.toPatchDto(setOf(GameField.PATH), false) } returns null
        every { game.toPatchDto(setOf(GameField.PATH), true) } returns adminPatch

        processor.process(EntityChangeSet().apply {
            game(1L).apply { rowUpdated = true; fields += GameField.PATH }
        }.finalizeTransaction())

        assertTrue(gameUserEvents.isEmpty())
        assertEquals(listOf(GameEvent(EntityEventType.UPDATED, 1L, patch = adminPatch)), gameAdminEvents)
        verify(exactly = 0) { gameRepository.bumpUpdatedAt(any(), any()) }
    }

    @Test
    fun `transient entities should be ignored`() {
        processor.process(EntityChangeSet().apply {
            game(1L).apply { created = true; deleted = true }
            library(2L).apply { created = true; deleted = true }
            collection(3L).apply { created = true; deleted = true }
        })

        assertTrue(gameUserEvents.isEmpty())
        assertTrue(gameAdminEvents.isEmpty())
        assertTrue(libraryUserEvents.isEmpty())
        assertTrue(libraryAdminEvents.isEmpty())
        assertTrue(collectionEvents.isEmpty())
    }

    @Test
    fun `changed images should be resolved to their games and bump the timestamp`() {
        val game = mockGame(1L)
        val patch = GamePatchDto(Instant.now(), listOf(GameField.COVER), GamePatchValuesDto())
        every { gameRepository.findIdsByCoverImageIds(listOf(10L)) } returns listOf(1L)
        every { gameRepository.findIdsByHeaderImageIds(any()) } returns emptyList()
        every { gameRepository.findIdsByGalleryImageIds(any()) } returns emptyList()
        every { gameRepository.findAllById(listOf(1L)) } returns listOf(game)
        every { game.toPatchDto(setOf(GameField.COVER), any()) } returns patch

        processor.process(EntityChangeSet().apply { imageIds += 10L })

        verify(exactly = 1) { gameRepository.bumpUpdatedAt(listOf(1L), any()) }
        assertEquals(listOf(GameEvent(EntityEventType.UPDATED, 1L, patch = patch)), gameUserEvents)
        assertEquals(listOf(GameEvent(EntityEventType.UPDATED, 1L, patch = patch)), gameAdminEvents)
    }

    @Test
    fun `changed directory mappings should be resolved to their libraries`() {
        val library = mockk<Library>(relaxed = true) { every { id } returns 2L }
        every { libraryRepository.findIdsByDirectoryMappingIds(listOf(30L)) } returns listOf(2L)
        every { libraryRepository.findAllById(listOf(2L)) } returns listOf(library)
        every { library.toPatchDto(setOf(LibraryField.DIRECTORIES), false) } returns null
        every { library.toPatchDto(setOf(LibraryField.DIRECTORIES), true) } returns mockk()

        processor.process(EntityChangeSet().apply { directoryMappingIds += 30L })

        verify(exactly = 1) { libraryRepository.bumpUpdatedAt(listOf(2L), any()) }
        assertTrue(libraryUserEvents.isEmpty())
        assertEquals(1, libraryAdminEvents.size)
    }

    @Test
    fun `collection game changes should bump timestamp and include game IDs`() {
        val collection = Collection(name = "C").apply { id = 3L; createdAt = Instant.now(); updatedAt = createdAt }
        every { collectionRepository.findAllById(listOf(3L)) } returns listOf(collection)
        every { collectionRepository.findGameIdsByCollectionIds(listOf(3L)) } returns listOf(
            arrayOf<Any>(3L, 12L),
            arrayOf<Any>(3L, 11L)
        )

        processor.process(EntityChangeSet().apply {
            collection(3L).addAssociationFields(listOf(CollectionField.GAME_IDS))
        }.finalizeTransaction())

        verify(exactly = 1) { collectionRepository.bumpUpdatedAt(listOf(3L), any()) }
        assertEquals(1, collectionEvents.size)
        val event = collectionEvents.first()
        assertEquals(EntityEventType.UPDATED, event.type)
        assertEquals(listOf(CollectionField.GAME_IDS), event.patch?.fields)
        assertEquals(listOf(11L, 12L), event.patch?.values?.gameIds)
    }

    @Test
    fun `created entities should not bump timestamps`() {
        val collection = Collection(name = "C").apply { id = 3L; createdAt = Instant.now(); updatedAt = createdAt }
        every { collectionRepository.findAllById(listOf(3L)) } returns listOf(collection)
        every { collectionRepository.findGameIdsByCollectionIds(listOf(3L)) } returns emptyList()

        processor.process(EntityChangeSet().apply {
            collection(3L).apply {
                created = true
                addAssociationFields(listOf(CollectionField.GAME_IDS))
            }
        }.finalizeTransaction())

        verify(exactly = 0) { collectionRepository.bumpUpdatedAt(any(), any()) }
        assertEquals(EntityEventType.CREATED, collectionEvents.single().type)
        assertEquals("C", collectionEvents.single().collection?.name)
    }

    @Test
    fun `missing entities should not emit events`() {
        processor.process(EntityChangeSet().apply {
            game(1L).apply { rowUpdated = true; fields += GameField.TITLE }
        })

        assertTrue(gameUserEvents.isEmpty())
        assertTrue(gameAdminEvents.isEmpty())
    }

    private fun mockGame(id: Long): Game = mockk(relaxed = true) { every { this@mockk.id } returns id }
}
