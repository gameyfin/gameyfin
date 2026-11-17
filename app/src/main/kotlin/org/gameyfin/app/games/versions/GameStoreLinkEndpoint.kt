package org.gameyfin.app.games.versions

import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role

data class GameStoreLinkDto(
    val id: Long,
    val gameId: Long,
    val store: String,
    val storeAppId: String,
    val tracked: Boolean,
    val edition: String?
)

fun GameStoreLink.toDto() = GameStoreLinkDto(id!!, gameId, store, storeAppId, tracked, edition)

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class GameStoreLinkEndpoint(
    private val linkService: GameStoreLinkService,
    private val editionSuggestions: EditionSuggestionService
) {

    fun getLinks(gameId: Long): List<GameStoreLinkDto> = linkService.getLinks(gameId).map { it.toDto() }

    fun addLink(gameId: Long, store: String, storeAppId: String): GameStoreLinkDto =
        linkService.addLink(gameId, store, storeAppId).toDto()

    fun removeLink(linkId: Long) = linkService.removeLink(linkId)

    fun setTracked(linkId: Long) = linkService.setTracked(linkId)

    fun setEdition(linkId: Long, edition: String?) = linkService.setEdition(linkId, edition)

    fun getEditionSuggestions(store: String, storeAppId: String): List<String> =
        editionSuggestions.suggest(store, storeAppId)
}
