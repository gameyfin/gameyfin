package org.gameyfin.app.games.versions

import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class GameStoreSearchEndpoint(private val searchService: GameStoreSearchService) {

    fun search(store: String, query: String): List<StoreSearchResultDto> = searchService.search(store, query)
}
