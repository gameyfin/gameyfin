package org.gameyfin.app.core.sync

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PreDestroy
import org.gameyfin.app.collections.CollectionService
import org.gameyfin.app.collections.dto.CollectionEvent
import org.gameyfin.app.collections.dto.CollectionField
import org.gameyfin.app.collections.extensions.toDto
import org.gameyfin.app.collections.extensions.toPatchDto
import org.gameyfin.app.collections.repositories.CollectionRepository
import org.gameyfin.app.collections.repositories.findGameIdsGroupedByCollectionId
import org.gameyfin.app.games.GameService
import org.gameyfin.app.games.dto.GameEvent
import org.gameyfin.app.games.dto.GameField
import org.gameyfin.app.games.extensions.toPatchDto
import org.gameyfin.app.games.extensions.toSummaryDto
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.libraries.LibraryRepository
import org.gameyfin.app.libraries.LibraryService
import org.gameyfin.app.libraries.dto.LibraryEvent
import org.gameyfin.app.libraries.dto.LibraryField
import org.gameyfin.app.libraries.extensions.toPatchDto
import org.gameyfin.app.libraries.extensions.toSummaryDto
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Turns committed [EntityChangeSet]s into real-time events for the clients:
 * - Created entities are sent as summary DTO
 * - Updated entities are sent as patch containing only the changed fields
 * - Deleted entities are sent as ID only
 *
 * Change sets are processed asynchronously on a single thread and coalesced over a short period of time,
 * so e.g. library scans (one transaction per game) don't cause an excessive amount of queries and events.
 */
@Component
class EntityChangeProcessor(
    private val gameRepository: GameRepository,
    private val libraryRepository: LibraryRepository,
    private val collectionRepository: CollectionRepository,
    transactionManager: PlatformTransactionManager
) {

    companion object {
        private val log = KotlinLogging.logger {}
        private const val COALESCING_DELAY_MS = 100L
        private const val MAX_IDS_PER_QUERY = 500
    }

    private val readTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        isReadOnly = true
    }

    private val writeTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    private val queue = ConcurrentLinkedQueue<EntityChangeSet>()
    private val drainScheduled = AtomicBoolean(false)
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "entity-change-processor").apply { isDaemon = true }
    }

    fun enqueue(changeSet: EntityChangeSet) {
        if (changeSet.isEmpty()) return
        queue.add(changeSet)
        if (drainScheduled.compareAndSet(false, true)) {
            try {
                executor.schedule(::drain, COALESCING_DELAY_MS, TimeUnit.MILLISECONDS)
            } catch (e: Exception) {
                drainScheduled.set(false)
                log.debug(e) { "Could not schedule processing of entity changes" }
            }
        }
    }

    @PreDestroy
    fun shutdown() {
        executor.shutdownNow()
    }

    private fun drain() {
        // Reset first, so change sets enqueued while processing schedule another run
        drainScheduled.set(false)

        val changeSets = generateSequence { queue.poll() }.toList()
        if (changeSets.isEmpty()) return

        try {
            process(EntityChangeSet.merge(changeSets))
        } catch (e: Exception) {
            log.error { "Error while processing entity changes: ${e.message}" }
            log.debug(e) {}
        }
    }

    fun process(changes: EntityChangeSet) {
        resolveChildOwners(changes)
        bumpTimestamps(changes)
        val events = readTransaction.execute { buildEvents(changes) }
        events.emit()
    }

    /**
     * Child entities (e.g. images or field metadata) can change without their owner being touched.
     * Find the owners and mark the respective fields as changed.
     */
    private fun resolveChildOwners(changes: EntityChangeSet) {
        if (changes.gameFieldMetadataIds.isEmpty() && changes.imageIds.isEmpty() && changes.directoryMappingIds.isEmpty()) {
            return
        }

        readTransaction.executeWithoutResult {
            chunked(changes.gameFieldMetadataIds) { gameRepository.findIdsByFieldMetadataIds(it) }
                .forEach { changes.game(it).addAssociationFields(listOf(GameField.FIELD_METADATA)) }

            chunked(changes.imageIds) { gameRepository.findIdsByCoverImageIds(it) }
                .forEach { changes.game(it).addAssociationFields(listOf(GameField.COVER)) }
            chunked(changes.imageIds) { gameRepository.findIdsByHeaderImageIds(it) }
                .forEach { changes.game(it).addAssociationFields(listOf(GameField.HEADER)) }
            chunked(changes.imageIds) { gameRepository.findIdsByGalleryImageIds(it) }
                .forEach { changes.game(it).addAssociationFields(listOf(GameField.IMAGES)) }

            chunked(changes.directoryMappingIds) { libraryRepository.findIdsByDirectoryMappingIds(it) }
                .forEach { changes.library(it).addAssociationFields(listOf(LibraryField.DIRECTORIES)) }
        }
    }

    /**
     * Changes of collections or child entities don't update the owner's row and therefore its `updatedAt` timestamp.
     * The timestamp is required for delta synchronization, so it is updated explicitly.
     */
    private fun bumpTimestamps(changes: EntityChangeSet) {
        val gameIds = changes.games.idsRequiringTimestampBump()
        val libraryIds = changes.libraries.idsRequiringTimestampBump()
        val collectionIds = changes.collections.idsRequiringTimestampBump()
        if (gameIds.isEmpty() && libraryIds.isEmpty() && collectionIds.isEmpty()) return

        val now = Instant.now()
        writeTransaction.executeWithoutResult {
            gameIds.chunked(MAX_IDS_PER_QUERY).forEach { gameRepository.bumpUpdatedAt(it, now) }
            libraryIds.chunked(MAX_IDS_PER_QUERY).forEach { libraryRepository.bumpUpdatedAt(it, now) }
            collectionIds.chunked(MAX_IDS_PER_QUERY).forEach { collectionRepository.bumpUpdatedAt(it, now) }
        }
    }

    private fun buildEvents(changes: EntityChangeSet): PendingEvents {
        val events = PendingEvents()

        /* Games */
        val gameChanges = changes.games.filterValues { !it.isTransient }
        val games = gameRepository.findAllById(gameChanges.idsToLoad()).associateBy { it.id!! }
        gameChanges.forEach { (id, change) ->
            when {
                change.deleted -> {
                    events.gameUser += GameEvent(EntityEventType.DELETED, id)
                    events.gameAdmin += GameEvent(EntityEventType.DELETED, id)
                }

                change.created -> games[id]?.let { game ->
                    events.gameUser += GameEvent(EntityEventType.CREATED, id, summary = game.toSummaryDto(false))
                    events.gameAdmin += GameEvent(EntityEventType.CREATED, id, summary = game.toSummaryDto(true))
                }

                else -> games[id]?.let { game ->
                    game.toPatchDto(change.fields, false)?.let {
                        events.gameUser += GameEvent(EntityEventType.UPDATED, id, patch = it)
                    }
                    game.toPatchDto(change.fields, true)?.let {
                        events.gameAdmin += GameEvent(EntityEventType.UPDATED, id, patch = it)
                    }
                }
            }
        }

        /* Libraries */
        val libraryChanges = changes.libraries.filterValues { !it.isTransient }
        val libraries = libraryRepository.findAllById(libraryChanges.idsToLoad()).associateBy { it.id!! }
        libraryChanges.forEach { (id, change) ->
            when {
                change.deleted -> {
                    events.libraryUser += LibraryEvent(EntityEventType.DELETED, id)
                    events.libraryAdmin += LibraryEvent(EntityEventType.DELETED, id)
                }

                change.created -> libraries[id]?.let { library ->
                    events.libraryUser += LibraryEvent(
                        EntityEventType.CREATED,
                        id,
                        summary = library.toSummaryDto(false)
                    )
                    events.libraryAdmin += LibraryEvent(
                        EntityEventType.CREATED,
                        id,
                        summary = library.toSummaryDto(true)
                    )
                }

                else -> libraries[id]?.let { library ->
                    library.toPatchDto(change.fields, false)?.let {
                        events.libraryUser += LibraryEvent(EntityEventType.UPDATED, id, patch = it)
                    }
                    library.toPatchDto(change.fields, true)?.let {
                        events.libraryAdmin += LibraryEvent(EntityEventType.UPDATED, id, patch = it)
                    }
                }
            }
        }

        /* Collections */
        val collectionChanges = changes.collections.filterValues { !it.isTransient }
        val collectionIdsToLoad = collectionChanges.idsToLoad()
        val collections = collectionRepository.findAllById(collectionIdsToLoad).associateBy { it.id!! }
        val gameIdsByCollection = collectionChanges
            .filter { (id, change) -> id in collections && (change.created || CollectionField.GAME_IDS in change.fields) }
            .keys
            .let { ids ->
                ids.chunked(MAX_IDS_PER_QUERY)
                    .flatMap { collectionRepository.findGameIdsGroupedByCollectionId(it).entries }
            }
            .associate { it.key to it.value }

        collectionChanges.forEach { (id, change) ->
            when {
                change.deleted -> events.collection += CollectionEvent(EntityEventType.DELETED, id)

                change.created -> collections[id]?.let { collection ->
                    events.collection += CollectionEvent(
                        EntityEventType.CREATED,
                        id,
                        collection = collection.toDto(gameIdsByCollection[id] ?: emptyList())
                    )
                }

                else -> collections[id]?.let { collection ->
                    collection.toPatchDto(change.fields, gameIdsByCollection[id] ?: emptyList())?.let {
                        events.collection += CollectionEvent(EntityEventType.UPDATED, id, patch = it)
                    }
                }
            }
        }

        return events
    }

    private fun <F : Enum<F>> Map<Long, EntityChange<F>>.idsToLoad(): List<Long> =
        filterValues { !it.deleted && (it.created || it.fields.isNotEmpty()) }.keys.toList()

    private fun <F : Enum<F>> Map<Long, EntityChange<F>>.idsRequiringTimestampBump(): List<Long> =
        filterValues { it.requiresTimestampBump && !it.created && !it.deleted }.keys.toList()

    private fun chunked(ids: Set<Long>, query: (List<Long>) -> List<Long>): Set<Long> =
        ids.chunked(MAX_IDS_PER_QUERY).flatMap(query).toSet()

    private class PendingEvents {
        val gameUser = mutableListOf<GameEvent>()
        val gameAdmin = mutableListOf<GameEvent>()
        val libraryUser = mutableListOf<LibraryEvent>()
        val libraryAdmin = mutableListOf<LibraryEvent>()
        val collection = mutableListOf<CollectionEvent>()

        fun emit() {
            gameUser.forEach { GameService.emitUser(it) }
            gameAdmin.forEach { GameService.emitAdmin(it) }
            libraryUser.forEach { LibraryService.emitUser(it) }
            libraryAdmin.forEach { LibraryService.emitAdmin(it) }
            collection.forEach { CollectionService.emit(it) }
        }
    }
}
