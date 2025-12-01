package org.gameyfin.app.collections

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.collections.dto.*
import org.gameyfin.app.collections.extensions.toAdminDto
import org.gameyfin.app.collections.extensions.toDto
import org.gameyfin.app.collections.extensions.toUserDto
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
    fun deleteCollection(collectionId: Long) = collectionService.delete(collectionId)

    /* Unused endpoints for Hilla to generate typescript classes */

    @Suppress("Unused", "FunctionName")
    @RolesAllowed(Role.Names.ADMIN)
    fun _getAdminDto(id: Long): CollectionAdminDto = collectionService.getById(id).toAdminDto()

    @Suppress("Unused", "FunctionName")
    @RolesAllowed(Role.Names.ADMIN)
    fun _getUserDto(id: Long): CollectionUserDto = collectionService.getById(id).toUserDto()
}
