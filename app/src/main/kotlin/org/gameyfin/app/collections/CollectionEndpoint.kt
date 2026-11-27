package org.gameyfin.app.collections

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import com.vaadin.hilla.exception.EndpointException
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.collections.dto.*
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.annotations.DynamicPublicAccess
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.core.tasks.DeletionTaskService
import org.gameyfin.app.social.MemberIdentity
import org.gameyfin.app.users.UserEvents
import org.gameyfin.pluginapi.gamemetadata.Genre
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.gameyfin.pluginapi.gamemetadata.Theme
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import java.time.Instant

@Endpoint
@DynamicPublicAccess
@AnonymousAllowed
@Suppress("unused")
class CollectionEndpoint(
    private val collectionService: CollectionService,
    private val deletionTaskService: DeletionTaskService,
    private val members: MemberIdentity,
    private val userEvents: UserEvents,
    private val smartCollections: SmartCollectionService
) {
    fun subscribeToCollections(): Flux<List<CollectionDto>> {
        val auth = getCurrentAuth()
        val memberId = members.id(auth)
        return Flux.merge(
            CollectionService.changes(),
            userEvents.changes().map { },
            ConfigService.subscribe().filter { updates ->
                updates.any { ConfigProperties.Security.AllowPublicAccess.key in it.updates }
            }.map { },
            Flux.just(Unit)
        ).onBackpressureLatest().publishOn(Schedulers.boundedElastic(), 1).map {
            if (memberId != null && auth?.isAuthenticated != true) emptyList()
            else collectionService.sync(null, members.collectionViewer(auth)).collections
        }.distinctUntilChanged()
    }

    /**
     * Delta synchronization of the collections.
     *
     * @param since `serverTime` of the last synchronization, `null` to fetch all collections
     */
    fun sync(since: Instant?): CollectionSyncDto = collectionService.sync(since)

    fun getById(id: Long): CollectionDto? = collectionService.getDtoById(id)

    @PermitAll
    fun getSmartEditor(collectionId: Long?) = SmartCollectionEditor(
        collectionId?.let(smartCollections::settings) ?: SmartCollectionSettings(enabled = false),
        Genre.entries.map { SmartRuleOption(it.name, it.displayName) }.sortedBy { it.label.lowercase() },
        Theme.entries.map { SmartRuleOption(it.name, it.displayName) }.sortedBy { it.label.lowercase() },
        Platform.entries.map { SmartRuleOption(it.name, it.displayName) }.sortedBy { it.label.lowercase() }
    )

    @PermitAll
    fun previewSmartCollection(collectionId: Long, rule: SmartRule) =
        validateCollectionRequest { smartCollections.preview(collectionId, rule) }

    @PermitAll
    fun saveSmartSettings(collectionId: Long, settings: SmartCollectionSettings) =
        validateCollectionRequest { smartCollections.save(collectionId, settings) }

    private fun <T> validateCollectionRequest(request: () -> T): T = try {
        request()
    } catch (error: IllegalArgumentException) {
        throw EndpointException(error.message ?: "Invalid collection request")
    }

    @PermitAll
    fun ensureFavourites() = collectionService.ensureFavourites()

    @PermitAll
    fun setFavourite(gameId: Long, present: Boolean) = collectionService.setFavourite(gameId, present)

    @PermitAll
    fun createCustomCollection(dto: CollectionCreateDto) =
        validateCollectionRequest { collectionService.createCustom(dto) }

    @PermitAll
    fun updateOwnedCollection(dto: CollectionUpdateDto) = collectionService.updateOwned(dto)

    @PermitAll
    fun setOwnedMembership(collectionId: Long, gameId: Long, present: Boolean) =
        collectionService.setOwnedMembership(collectionId, gameId, present)

    @PermitAll
    fun deleteOwnedCollection(collectionId: Long) = collectionService.deleteOwned(collectionId)

    @RolesAllowed(Role.Names.ADMIN)
    fun createCollection(dto: CollectionCreateDto) =
        validateCollectionRequest { collectionService.create(dto) }

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
