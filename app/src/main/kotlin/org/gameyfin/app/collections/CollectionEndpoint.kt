package org.gameyfin.app.collections

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.collections.dto.CollectionCreateDto
import org.gameyfin.app.collections.dto.CollectionDto
import org.gameyfin.app.collections.dto.CollectionEvent
import org.gameyfin.app.collections.dto.CollectionUpdateDto
import org.gameyfin.app.collections.extensions.toDto
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.annotations.DynamicPublicAccess
import org.gameyfin.app.core.security.isCurrentUserAdmin
import reactor.core.publisher.Flux

@Endpoint
@DynamicPublicAccess
@AnonymousAllowed
class CollectionEndpoint(
    private val collectionService: CollectionService
) {
    fun subscribeToCollectionEvents(): Flux<out List<CollectionEvent>> {
        return if (isCurrentUserAdmin()) {
            CollectionService.subscribeAdmin()
        } else {
            CollectionService.subscribeUser()
        }
    }

    fun getAll(): List<CollectionDto> = collectionService.getAll()

    fun getById(id: Long): CollectionDto = collectionService.getById(id).toDto()

    @RolesAllowed(Role.Names.ADMIN)
    fun createCollection(dto: CollectionCreateDto): CollectionDto = collectionService.create(dto)

    @RolesAllowed(Role.Names.ADMIN)
    fun updateCollection(dto: CollectionUpdateDto): CollectionDto = collectionService.update(dto)

    @RolesAllowed(Role.Names.ADMIN)
    fun addGameToCollection(collectionId: Long, gameId: Long): CollectionDto =
        collectionService.addGame(collectionId, gameId)

    @RolesAllowed(Role.Names.ADMIN)
    fun removeGameFromCollection(collectionId: Long, gameId: Long): CollectionDto =
        collectionService.removeGame(collectionId, gameId)

    @RolesAllowed(Role.Names.ADMIN)
    fun deleteCollection(collectionId: Long) = collectionService.delete(collectionId)
}
