package org.gameyfin.app.games

import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role
import org.gameyfin.app.games.dto.GameDto
import org.gameyfin.app.games.dto.GameEvent
import org.gameyfin.app.games.dto.GameSearchResultDto
import org.gameyfin.app.games.dto.GameUpdateDto
import org.gameyfin.app.games.dto.OriginalIdDto
import org.gameyfin.app.libraries.LibraryService
import reactor.core.publisher.Flux
import java.nio.file.Path

@Endpoint
@PermitAll
class GameEndpoint(
    private val gameService: GameService,
    private val libraryService: LibraryService
) {
    fun subscribe(): Flux<List<GameEvent>> {
        return GameService.subscribe()
    }

    fun getAll(): List<GameDto> = gameService.getAll()

    @RolesAllowed(Role.Names.ADMIN)
    fun updateGame(game: GameUpdateDto) = gameService.update(game)

    @RolesAllowed(Role.Names.ADMIN)
    fun deleteGame(gameId: Long) {
        libraryService.deleteGameFromLibrary(gameId)
        gameService.delete(gameId)
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun getPotentialMatches(searchTerm: String): List<GameSearchResultDto> {
        return gameService.getPotentialMatches(searchTerm)
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun matchManually(originalIds: Map<String, OriginalIdDto>, path: String, libraryId: Long, replaceGameId: Long?) {
        val library = libraryService.getById(libraryId)
        val game = gameService.matchManually(originalIds, Path.of(path), library, replaceGameId)
        if (game != null) {
            libraryService.addGamesToLibrary(listOf(game), library, true)
        }
    }
}