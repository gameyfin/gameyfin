package de.grimsi.gameyfin.libraries

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Role
import de.grimsi.gameyfin.games.GameService
import de.grimsi.gameyfin.games.dto.GameDto
import jakarta.annotation.security.RolesAllowed

@Endpoint
class LibraryEndpoint(
    private val libraryService: LibraryService,
    private val gameService: GameService
) {
    @RolesAllowed(Role.Names.ADMIN)
    fun getAllLibraries(): Collection<LibraryDto> {
        return libraryService.getAllLibraries()
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun createLibrary(library: LibraryDto): LibraryDto {
        return libraryService.createOrUpdate(library)
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun test(testString: String): GameDto {
        return libraryService.test(testString)
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun removeGames() {
        return gameService.deleteAll()
    }
}