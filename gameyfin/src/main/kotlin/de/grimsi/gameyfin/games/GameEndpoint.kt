package de.grimsi.gameyfin.games

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Role
import de.grimsi.gameyfin.games.dto.GameDto
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed

@Endpoint
@PermitAll
class GameEndpoint(
    private val gameService: GameService
) {

    fun getGame(id: Long): GameDto {
        return gameService.getGame(id)
    }

    fun getMostRecentlyAddedGames(n: Int?): List<GameDto> {
        return gameService.getMostRecentlyAdded(n ?: 10)
    }

    fun getMostRecentlyUpdatedGames(n: Int?): List<GameDto> {
        return gameService.getMostRecentlyUpdated(n ?: 10)
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun removeGames() {
        return gameService.deleteAll()
    }
}