package de.grimsi.gameyfin.games

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Role
import de.grimsi.gameyfin.games.dto.GameDto
import de.grimsi.gameyfin.games.dto.GameEvent
import de.grimsi.gameyfin.games.dto.GameUpdateDto
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import reactor.core.publisher.Flux

@Endpoint
@PermitAll
class GameEndpoint(
    private val gameService: GameService
) {
    fun subscribe(): Flux<List<GameEvent>> {
        return GameService.subscribe()
    }

    fun getAll(): List<GameDto> = gameService.getAll()

    @RolesAllowed(Role.Names.ADMIN)
    fun updateGame(game: GameUpdateDto) = gameService.update(game)

    @RolesAllowed(Role.Names.ADMIN)
    fun deleteGame(gameId: Long) = gameService.delete(gameId)
}