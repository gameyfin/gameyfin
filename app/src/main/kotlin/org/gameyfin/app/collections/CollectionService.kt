package org.gameyfin.app.collections

import org.gameyfin.app.collections.dto.*
import org.gameyfin.app.collections.entities.Collection
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
        private val collectionUserEvents =
            Sinks.many().multicast().onBackpressureBuffer<CollectionUserEvent>(256, false)
        private val collectionAdminEvents =
            Sinks.many().multicast().onBackpressureBuffer<CollectionAdminEvent>(256, false)

        fun subscribeUser(): Flux<List<CollectionUserEvent>> =
            collectionUserEvents.asFlux().buffer(100.milliseconds.toJavaDuration())

        fun subscribeAdmin(): Flux<List<CollectionAdminEvent>> =
            collectionAdminEvents.asFlux().buffer(100.milliseconds.toJavaDuration())

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
    fun create(dto: CollectionCreateDto): CollectionDto {
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
        val saved = collectionRepository.save(entity)

        return saved.toDto()
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
        val saved = collectionRepository.save(collection)

        return saved.toDto()
    }

    @Transactional
    fun addGame(collectionId: Long, gameId: Long): CollectionDto {
        val collection = getById(collectionId)
        val game = gameService.getById(gameId)
        collection.addGame(game)
        val saved = collectionRepository.save(collection)

        return saved.toDto()
    }

    @Transactional
    fun removeGame(collectionId: Long, gameId: Long): CollectionDto {
        val collection = getById(collectionId)
        val game = gameService.getById(gameId)
        collection.removeGame(game)
        val saved = collectionRepository.save(collection)

        return saved.toDto()
    }

    fun delete(collectionId: Long) {
        collectionRepository.deleteById(collectionId)
    }
}
