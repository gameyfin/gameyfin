package de.grimsi.gameyfin.libraries

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Role
import de.grimsi.gameyfin.libraries.entities.Library
import jakarta.annotation.security.RolesAllowed

@Endpoint
class LibraryEndpoint(
    private val libraryService: LibraryService
) {
    @RolesAllowed(Role.Names.ADMIN)
    fun getAllLibraries(): Collection<Library> {
        return libraryService.getAllLibraries()
    }


}