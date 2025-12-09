package org.gameyfin.app.collections

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.collections.dto.*
import org.gameyfin.app.collections.entities.Collection
import org.gameyfin.app.collections.entities.CollectionMetadata
import org.gameyfin.app.collections.extensions.toDto
import org.gameyfin.app.collections.extensions.toEntity
import org.gameyfin.app.collections.repositories.CollectionRepository
import org.gameyfin.app.games.GameService
import org.gameyfin.app.games.entities.Game
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

@Service
class CollectionService(
    private val collectionRepository: CollectionRepository,
    private val gameService: GameService
) {
    companion object {
        private val log = KotlinLogging.logger {}

        private val collectionUserEvents =
            Sinks.many().multicast().onBackpressureBuffer<CollectionUserEvent>(1024, false)
        private val collectionAdminEvents =
            Sinks.many().multicast().onBackpressureBuffer<CollectionAdminEvent>(1024, false)

        fun subscribeUser(): Flux<List<CollectionUserEvent>> {
            log.debug { "New user subscription for collectionUserEvents" }
            return collectionUserEvents.asFlux()
                .buffer(100.milliseconds.toJavaDuration())
                .doOnSubscribe {
                    log.debug { "Subscriber added to user collectionUserEvents [${collectionUserEvents.currentSubscriberCount()}]" }
                }
                .doFinally {
                    log.debug { "Subscriber removed from user collectionUserEvents with signal type $it [${collectionUserEvents.currentSubscriberCount()}]" }
                }
        }

        fun subscribeAdmin(): Flux<List<CollectionAdminEvent>> {
            log.debug { "New admin subscription for collectionAdminEvents" }
            return collectionAdminEvents.asFlux()
                .buffer(100.milliseconds.toJavaDuration())
                .doOnSubscribe {
                    log.debug { "Subscriber added to admin collectionAdminEvents [${collectionAdminEvents.currentSubscriberCount()}]" }
                }
                .doFinally {
                    log.debug { "Subscriber removed from admin collectionAdminEvents with signal type $it [${collectionAdminEvents.currentSubscriberCount()}]" }
                }
        }

        fun emitUser(event: CollectionUserEvent) {
            collectionUserEvents.tryEmitNext(event)
        }

        fun emitAdmin(event: CollectionAdminEvent) {
            collectionAdminEvents.tryEmitNext(event)
        }
    }

    fun getAll(): List<CollectionDto> = collectionRepository.findAll().map { it.toDto() }

    fun getById(id: Long): Collection = collectionRepository.findByIdOrNull(id)
        ?: throw IllegalArgumentException("Collection with id $id not found")

    @Transactional
    fun create(dto: CollectionCreateDto) {
        if (collectionRepository.findByName(dto.name) != null) {
            throw IllegalArgumentException("Collection with name '${dto.name}' already exists")
        }
        val entity = dto.toEntity()
        dto.gameIds?.let { ids ->
            ids.distinct().forEach { gameId ->
                val game = gameService.getById(gameId)
                entity.addGame(game)
            }
        }
        collectionRepository.save(entity)
    }

    @Transactional
    fun update(dto: CollectionUpdateDto): CollectionDto {
        val collection = getById(dto.id)
        dto.name?.let { newName ->
            if (newName != collection.name && collectionRepository.findByName(newName) != null) {
                throw IllegalArgumentException("Collection with name '$newName' already exists")
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

        val saved = collectionRepository.save(collection)

        return saved.toDto()
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
        gameService.update(game)

        val saved = collectionRepository.save(collection)

        return saved.toDto()
    }

    @Transactional
    fun removeGame(collectionId: Long, gameId: Long): CollectionDto {
        val collection = getById(collectionId)
        val game = gameService.getById(gameId)

        collection.removeGame(game)
        gameService.update(game)

        val saved = collectionRepository.save(collection)

        return saved.toDto()
    }

    fun delete(collectionId: Long) {
        collectionRepository.deleteById(collectionId)
    }
}
