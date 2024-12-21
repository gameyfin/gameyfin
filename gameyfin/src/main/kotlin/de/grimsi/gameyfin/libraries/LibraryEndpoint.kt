package de.grimsi.gameyfin.libraries

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Role
import de.grimsi.gameyfin.games.entities.Game
import jakarta.annotation.security.RolesAllowed

@Endpoint
class LibraryEndpoint(
    private val libraryService: LibraryService
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
    fun test(testString: String): Game {
        return libraryService.test(testString)
    }
}