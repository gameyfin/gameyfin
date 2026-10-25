package org.gameyfin.app.games

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.annotations.DynamicPublicAccess
import org.gameyfin.app.core.plugins.dto.ExternalProviderIdDto
import org.gameyfin.app.core.security.isCurrentUserAdmin
import org.gameyfin.app.games.dto.*
import org.gameyfin.app.libraries.LibraryCoreService
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import java.nio.file.Path
import java.time.Instant

@Endpoint
@DynamicPublicAccess
@AnonymousAllowed
class GameEndpoint(
    private val gameService: GameService,
    private val libraryCoreService: LibraryCoreService
) {
    fun subscribe(): Flux<out List<GameEvent>> {
        return if (isCurrentUserAdmin()) {
            GameService.subscribeAdmin()
        } else {
            GameService.subscribeUser()
        }
    }

    /**
     * Delta synchronization of the game summaries used by all overview pages.
     *
     * @param since `serverTime` of the last synchronization, `null` to fetch all games
     */
    fun sync(since: Instant?): GameSyncDto = gameService.sync(since, isCurrentUserAdmin())

    /**
     * Returns the full details of a game, `null` if it does not exist.
     */
    fun getById(id: Long): GameDto? = gameService.getDtoById(id)

    /**
     * Returns the full details of multiple games. Non-existing games are omitted.
     */
    fun getByIds(ids: List<Long>): List<GameDto> = gameService.getDtosByIds(ids)

    fun getPotentialMatches(searchTerm: String, platformFilter: Set<Platform>): List<GameSearchResultDto> {
        return gameService.getPotentialMatches(searchTerm, platformFilter)
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun updateGame(game: GameUpdateDto) = gameService.edit(game)

    @RolesAllowed(Role.Names.ADMIN)
    @Transactional
    fun createGame(title: String, path: String, libraryId: Long): Long {
        val library = libraryCoreService.getWithGamesAndIgnoredPaths(libraryId)
            ?: throw IllegalArgumentException("Library with id $libraryId not found")
        val game = gameService.createManually(title, path, library)
        libraryCoreService.addGamesToLibrary(listOf(game), library, true)
        return game.id!!
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun deleteGame(gameId: Long) {
        libraryCoreService.deleteGameFromLibrary(gameId)
        gameService.delete(gameId)
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun matchManually(
        originalIds: Map<String, ExternalProviderIdDto>,
        path: String,
        libraryId: Long,
        replaceGameId: Long?
    ) {
        val library = libraryCoreService.getWithGamesAndIgnoredPaths(libraryId)
            ?: throw IllegalArgumentException("Library with id $libraryId not found")
        val game = gameService.matchManually(originalIds, Path.of(path), library, replaceGameId)
        if (game != null) {
            libraryCoreService.addGamesToLibrary(listOf(game), library, true)
        }
    }

    /**
     * This endpoint is necessary to fetch enum property values from the backend.
     * Hilla only generates enums directly from their respective values and ignores the displayName property.
     */
    @RolesAllowed(Role.Names.ADMIN)
    fun getEnumPropertyValues(): GameEnumPropertyValuesDto {
        return gameService.getEnumPropertyValues()
    }
}
