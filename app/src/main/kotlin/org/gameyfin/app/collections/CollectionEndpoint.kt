package org.gameyfin.app.collections

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.collections.dto.*
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.annotations.DynamicPublicAccess
import org.gameyfin.app.core.tasks.DeletionTaskService
import reactor.core.publisher.Flux
import java.time.Instant

@Endpoint
@DynamicPublicAccess
@AnonymousAllowed
@Suppress("unused")
class CollectionEndpoint(
    private val collectionService: CollectionService,
    private val deletionTaskService: DeletionTaskService
) {
    fun subscribeToCollectionEvents(): Flux<out List<CollectionEvent>> = CollectionService.subscribe()

    /**
     * Delta synchronization of the collections.
     *
     * @param since `serverTime` of the last synchronization, `null` to fetch all collections
     */
    fun sync(since: Instant?): CollectionSyncDto = collectionService.sync(since)

    fun getById(id: Long): CollectionDto? = collectionService.getDtoById(id)

    @RolesAllowed(Role.Names.ADMIN)
    fun createCollection(dto: CollectionCreateDto) = collectionService.create(dto)

    @RolesAllowed(Role.Names.ADMIN)
    fun updateCollection(dto: CollectionUpdateDto) = collectionService.update(dto)

    @RolesAllowed(Role.Names.ADMIN)
    fun updateCollections(collections: List<CollectionUpdateDto>) = collectionService.update(collections)

    @RolesAllowed(Role.Names.ADMIN)
    fun addGameToCollection(collectionId: Long, gameId: Long) =
        collectionService.addGame(collectionId, gameId)

    @RolesAllowed(Role.Names.ADMIN)
    fun removeGameFromCollection(collectionId: Long, gameId: Long) =
        collectionService.removeGame(collectionId, gameId)

    @RolesAllowed(Role.Names.ADMIN)
    fun deleteCollection(collectionId: Long) = deletionTaskService.deleteCollection(collectionId)
}
