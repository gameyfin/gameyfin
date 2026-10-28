package org.gameyfin.app.collections

import org.gameyfin.app.collections.dto.*
import org.gameyfin.app.collections.entities.Collection
import org.gameyfin.app.collections.entities.CollectionMetadata
import org.gameyfin.app.collections.extensions.toDto
import org.gameyfin.app.collections.extensions.toEntity
import org.gameyfin.app.collections.repositories.CollectionRepository
import org.gameyfin.app.collections.repositories.findGameIdsGroupedByCollectionId
import org.gameyfin.app.core.sync.EntityEventBroadcaster
import org.gameyfin.app.core.sync.SYNC_SAFETY_WINDOW
import org.gameyfin.app.games.GameService
import org.gameyfin.app.games.entities.Game
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import java.time.Instant

@Service
class CollectionService(
    private val collectionRepository: CollectionRepository,
    private val gameService: GameService
) {
    companion object {
        /* Websockets (collections contain no admin-only data, so all users share one stream) */
        private val events = EntityEventBroadcaster<CollectionEvent>("collectionEvents")

        fun subscribe(): Flux<List<CollectionEvent>> = events.subscribe()

        fun emit(event: CollectionEvent) = events.emit(event)
    }

    /**
     * Returns the collections that changed since the given timestamp, or all if [since] is `null`.
     * Clients have to remove all collections that are not contained in [CollectionSyncDto.allIds] (delta sync only).
     */
    @Transactional(readOnly = true)
    fun sync(since: Instant?): CollectionSyncDto {
        val serverTime = Instant.now()

        if (since == null) {
            return CollectionSyncDto(
                serverTime = serverTime,
                full = true,
                collections = collectionRepository.findAll().toDtos(),
                allIds = emptyList()
            )
        }

        return CollectionSyncDto(
            serverTime = serverTime,
            full = false,
            collections = collectionRepository.findAllByUpdatedAtAfter(since.minus(SYNC_SAFETY_WINDOW)).toDtos(),
            allIds = collectionRepository.findAllIds()
        )
    }

    @Transactional(readOnly = true)
    fun getDtoById(id: Long): CollectionDto? =
        collectionRepository.findByIdOrNull(id)?.let { listOf(it).toDtos().first() }

    private fun List<Collection>.toDtos(): List<CollectionDto> {
        val gameIds = collectionRepository.findGameIdsGroupedByCollectionId(this.mapNotNull { it.id })
        return this.map { it.toDto(gameIds[it.id] ?: emptyList()) }
    }

    fun getById(id: Long): Collection = collectionRepository.findByIdOrNull(id)
        ?: throw IllegalArgumentException("Collection with id $id not found")

    @Transactional
    fun create(dto: CollectionCreateDto) {
        createEntity(dto)
    }

    @Transactional
    fun createReturningId(dto: CollectionCreateDto): Long = createEntity(dto).id!!

    private fun createEntity(dto: CollectionCreateDto): Collection {
        require(collectionRepository.findByName(dto.name) == null) {
            "Collection with name '${dto.name}' already exists"
        }
        val entity = dto.toEntity()
        dto.gameIds?.let { ids ->
            ids.distinct().forEach { gameId ->
                val game = gameService.getById(gameId)
                entity.addGame(game)
            }
        }
        return collectionRepository.save(entity)
    }

    @Transactional
    fun update(dto: CollectionUpdateDto): CollectionDto {
        val collection = getById(dto.id)
        dto.name?.let { newName ->
            require(collectionRepository.findByName(dto.name) == null) {
                "Collection with name '${dto.name}' already exists"
            }
            collection.name = newName
        }
        dto.description?.let { collection.description = it }
        dto.gameIds?.let { ids ->
            // Replace entire set of games
            val newGames: MutableList<Game> = mutableListOf()
            ids.distinct().forEach { gameId ->
                val game = gameService.getById(gameId)
                newGames.add(game)
            }
            // Remove old backrefs
            collection.games.forEach { it.collections.remove(collection) }
            collection.games.clear()
            newGames.forEach { collection.addGame(it) }
        }
        dto.metadata?.let {
            collection.metadata = CollectionMetadata(
                it.displayOnHomepage ?: collection.metadata.displayOnHomepage,
                it.displayOrder ?: collection.metadata.displayOrder,
                collection.metadata.gamesAddedAt
            )
        }

        val saved = collectionRepository.saveAndFlush(collection)

        return listOf(saved).toDtos().first()
    }

    /**
     * Updates multiple collections in the repository.
     */
    @Transactional
    fun update(collections: List<CollectionUpdateDto>) {
        collections.forEach { update(it) }
    }

    @Transactional
    fun addGame(collectionId: Long, gameId: Long): CollectionDto {
        val collection = getById(collectionId)
        val game = gameService.getById(gameId)

        collection.addGame(game)

        val saved = collectionRepository.saveAndFlush(collection)

        return listOf(saved).toDtos().first()
    }

    @Transactional
    fun removeGame(collectionId: Long, gameId: Long): CollectionDto {
        val collection = getById(collectionId)
        val game = gameService.getById(gameId)

        collection.removeGame(game)

        val saved = collectionRepository.saveAndFlush(collection)

        return listOf(saved).toDtos().first()
    }

    fun delete(collectionId: Long) {
        collectionRepository.deleteById(collectionId)
    }
}
