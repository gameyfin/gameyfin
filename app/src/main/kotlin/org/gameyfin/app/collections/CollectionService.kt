package org.gameyfin.app.collections

import org.gameyfin.app.collections.dto.CollectionCreateDto
import org.gameyfin.app.collections.dto.CollectionDto
import org.gameyfin.app.collections.dto.CollectionSyncDto
import org.gameyfin.app.collections.dto.CollectionUpdateDto
import org.gameyfin.app.collections.entities.Collection
import org.gameyfin.app.collections.entities.CollectionKind
import org.gameyfin.app.collections.entities.CollectionMetadata
import org.gameyfin.app.collections.entities.CollectionVisibility
import org.gameyfin.app.collections.extensions.toDto
import org.gameyfin.app.collections.extensions.toEntity
import org.gameyfin.app.collections.repositories.CollectionRepository
import org.gameyfin.app.collections.repositories.findGameIdsGroupedByCollectionId
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.core.sync.EntityEventBroadcaster
import org.gameyfin.app.core.sync.SYNC_SAFETY_WINDOW
import org.gameyfin.app.games.GameService
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.social.MemberIdentity
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import java.time.Instant
import java.util.*

@Service
class CollectionService(
    private val collectionRepository: CollectionRepository,
    private val gameService: GameService,
    private val members: MemberIdentity,
    private val config: ConfigService,
    private val smartCollections: SmartCollectionService
) {
    companion object {
        // Internal invalidations. Subscribers read a fresh snapshot using their own access rules.
        private val events = EntityEventBroadcaster<Unit>("collectionChanges")

        internal fun changes(): Flux<Unit> = events.subscribe().map(List<Unit>::first)

        internal fun invalidate() = events.emit(Unit)
    }

    @Transactional(readOnly = true)
    fun sync(since: Instant?): CollectionSyncDto = sync(since, members.collectionViewer())

    /**
     * Returns the collections that changed since the given timestamp, or all if [since] is `null`.
     * Clients have to remove all collections that are not contained in [CollectionSyncDto.allIds] (delta sync only).
     */
    @Transactional(readOnly = true)
    fun sync(since: Instant?, viewer: CollectionViewer): CollectionSyncDto {
        val serverTime = Instant.now()

        val visible = collectionRepository.findAll(viewer.readable())
        val cutoff = since?.minus(SYNC_SAFETY_WINDOW)
        val changed = if (cutoff == null) visible else visible.filter { it.updatedAt!!.isAfter(cutoff) }
        return CollectionSyncDto(
            serverTime = serverTime,
            full = since == null,
            collections = changed.toDtos(),
            allIds = if (since == null) emptyList() else visible.map { it.id!! }
        )
    }

    @Transactional(readOnly = true)
    fun getDtoById(id: Long): CollectionDto? =
        findReadable(id)?.let { listOf(it).toDtos().first() }

    fun findReadable(id: Long): Collection? =
        collectionRepository.findOne(members.collectionViewer().readable().and { root, _, cb ->
            cb.equal(root.get<Long>("id"), id)
        }).orElse(null)

    fun gameCollectionFilter(id: Long): Specification<Game> {
        val viewer = members.collectionViewer()
        return Specification { root, _, cb ->
            val collection = root.join<Game, Collection>("collections")
            cb.and(cb.equal(collection.get<Long>("id"), id), viewer.readable(collection, cb))
        }
    }

    fun findReadable(page: Pageable): Page<Collection> {
        val sort =
            Sort.by(page.sort.map { it.withProperty(if (it.property == "name") "displaySortName" else it.property) }
                .toList())
        return collectionRepository.findAll(
            members.collectionViewer().readable(), PageRequest.of(page.pageNumber, page.pageSize, sort)
        )
    }

    private fun List<Collection>.toDtos(): List<CollectionDto> {
        val gameIds = collectionRepository.findGameIdsGroupedByCollectionId(this.mapNotNull { it.id })
        return this.map { it.toDto(gameIds[it.id] ?: emptyList()) }
    }

    fun getById(id: Long): Collection = collectionRepository.findByIdOrNull(id)
        ?: throw IllegalArgumentException("Collection with id $id not found")

    fun getCuratedById(id: Long): Collection =
        getById(id).takeIf { it.kind == CollectionKind.CURATED }
            ?: throw NoSuchElementException("Collection not found")

    fun lockCuratedById(id: Long): Collection =
        collectionRepository.lockById(id)?.takeIf { it.kind == CollectionKind.CURATED }
            ?: throw NoSuchElementException("Collection not found")

    @Transactional
    fun create(dto: CollectionCreateDto) {
        createEntity(dto)
    }

    @Transactional
    fun createReturningId(dto: CollectionCreateDto): Long = createEntity(dto).id!!

    private fun createEntity(dto: CollectionCreateDto): Collection {
        validateName(dto.name)
        require(collectionRepository.findByName(dto.name) == null) {
            "Collection with name '${dto.name}' already exists"
        }
        return saveNew(dto.toEntity(), dto)
    }

    @Transactional
    fun update(dto: CollectionUpdateDto): CollectionDto {
        return updateEntity(lockCuratedById(dto.id), dto)
    }

    private fun updateEntity(collection: Collection, dto: CollectionUpdateDto): CollectionDto {
        if (dto.gameIds != null) collection.requireManualMembership()
        dto.name?.let { newName ->
            validateName(newName)
            if (collection.kind == CollectionKind.CURATED) {
                val existing = collectionRepository.findByName(newName)
                require(existing == null || existing.id == collection.id) {
                    "Collection with name '$newName' already exists"
                }
                collection.name = newName
            } else {
                collection.memberName = newName
            }
        }
        dto.description?.let { collection.description = it }
        dto.visibility?.let { collection.visibility = it }
        dto.gameIds?.let { ids ->
            // Replace entire set of games
            val newGames: MutableList<Game> = mutableListOf()
            ids.distinct().forEach { gameId ->
                val game = gameService.getById(gameId)
                newGames.add(game)
            }
            // Remove old backrefs and membership timestamps.
            collection.games.toList().forEach(collection::removeGame)
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
        collections.sortedBy { it.id }.forEach { update(it) }
    }

    @Transactional
    fun addGame(collectionId: Long, gameId: Long): CollectionDto {
        val collection = lockCuratedById(collectionId)
        collection.requireManualMembership()
        val game = gameService.getById(gameId)

        collection.addGame(game)

        val saved = collectionRepository.saveAndFlush(collection)

        return listOf(saved).toDtos().first()
    }

    @Transactional
    fun removeGame(collectionId: Long, gameId: Long): CollectionDto {
        val collection = lockCuratedById(collectionId)
        collection.requireManualMembership()
        val game = gameService.getById(gameId)

        collection.removeGame(game)

        val saved = collectionRepository.saveAndFlush(collection)

        return listOf(saved).toDtos().first()
    }

    fun allowUserCollections(): Boolean = config.get(ConfigProperties.Social.AllowUserCollections) == true

    @Transactional
    fun createCustom(dto: CollectionCreateDto): Long {
        if (!allowUserCollections()) throw AccessDeniedException("Custom collection creation is disabled")
        validateName(dto.name)
        val owner = members.lock(members.requireId())
        val collection = Collection(
            name = UUID.randomUUID().toString(), memberName = dto.name,
            description = dto.description, owner = owner, kind = CollectionKind.CUSTOM,
            visibility = dto.visibility ?: CollectionVisibility.PRIVATE,
            metadata = CollectionMetadata(displayOnHomepage = false)
        )
        return saveNew(collection, dto).id!!
    }

    private fun saveNew(collection: Collection, dto: CollectionCreateDto): Collection {
        if (dto.smartRule != null) {
            require(dto.gameIds.isNullOrEmpty()) { "Smart collection membership is managed by its rules" }
            smartCollections.enable(collection, dto.smartRule)
        } else {
            dto.gameIds.orEmpty().distinct().forEach { collection.addGame(gameService.getById(it)) }
        }
        return collectionRepository.save(collection)
    }

    @Transactional
    fun ensureFavourites(): Long = favourites().id!!

    @Transactional
    fun setFavourite(gameId: Long, present: Boolean) {
        val collection = favourites()
        val game = gameService.getById(gameId)
        if (present && game !in collection.games) collection.addGame(game)
        if (!present && game in collection.games) collection.removeGame(game)
    }

    private fun favourites(): Collection {
        val ownerId = members.requireId()
        val owner = members.lock(ownerId)
        return collectionRepository.findByFavouritesOwnerId(ownerId) ?: collectionRepository.save(
            Collection(
                name = UUID.randomUUID().toString(), memberName = "Your favourites",
                kind = CollectionKind.FAVOURITES, visibility = CollectionVisibility.PRIVATE,
                owner = owner, favouritesOwnerId = ownerId, metadata = CollectionMetadata(displayOnHomepage = false)
            )
        )
    }

    @Transactional
    fun updateOwned(dto: CollectionUpdateDto): CollectionDto {
        val collection = owned(dto.id)
        require(dto.metadata == null) { "Homepage placement requires administrator access" }
        if (collection.kind == CollectionKind.FAVOURITES) {
            require(dto.name == null && dto.description == null && dto.gameIds == null) { "Only visibility is editable" }
        }
        return updateEntity(collection, dto)
    }

    @Transactional
    fun setOwnedMembership(id: Long, gameId: Long, present: Boolean) {
        val collection = owned(id)
        require(collection.kind == CollectionKind.CUSTOM)
        collection.requireManualMembership()
        val game = gameService.getById(gameId)
        if (present && game !in collection.games) collection.addGame(game)
        if (!present && game in collection.games) collection.removeGame(game)
    }

    @Transactional
    fun deleteOwned(id: Long) {
        val collection = owned(id)
        require(collection.kind == CollectionKind.CUSTOM) { "Favourites cannot be deleted" }
        collectionRepository.delete(collection)
    }

    private fun owned(id: Long): Collection {
        val memberId = members.requireId()
        members.lock(memberId)
        return collectionRepository.lockById(id)?.takeIf { it.owner?.id == memberId }
            ?: throw AccessDeniedException("Collection owner required")
    }

    private fun validateName(name: String) {
        require(name.isNotBlank() && name.length <= 255) { "Collection name must have 1–255 characters" }
    }

    @Transactional
    fun delete(collectionId: Long) {
        collectionRepository.lockById(collectionId)?.let(collectionRepository::delete)
    }
}
