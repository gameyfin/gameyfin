package de.grimsi.gameyfin.libraries

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Role
import de.grimsi.gameyfin.games.GameService
import de.grimsi.gameyfin.games.dto.GameDto
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed

@Endpoint
@PermitAll
class LibraryEndpoint(
    private val libraryService: LibraryService,
    private val gameService: GameService
) {
    fun getAllLibraries(): Collection<LibraryDto> {
        return libraryService.getAllLibraries()
    }

    fun getGamesInLibrary(libraryId: Long): Collection<GameDto> {
        return libraryService.getGamesInLibrary(libraryId)
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun triggerScan(libraries: Collection<LibraryDto>?) {
        return libraryService.triggerScan(libraries)
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun createLibrary(library: LibraryDto): LibraryDto {
        return libraryService.createOrUpdate(library)
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun removeLibraries() {
        return libraryService.deleteAllLibraries()
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun removeGames() {
        return gameService.deleteAll()
    }
}