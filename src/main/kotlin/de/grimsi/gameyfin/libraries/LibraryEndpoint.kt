package de.grimsi.gameyfin.libraries

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.libraries.entities.Library
import de.grimsi.gameyfin.core.Roles
import jakarta.annotation.security.RolesAllowed

@Endpoint
class LibraryEndpoint(
    private val libraryService: LibraryService
) {
    @RolesAllowed(Roles.Names.SUPERADMIN, Roles.Names.ADMIN)
    fun getAllLibraries(): Collection<Library> {
        return libraryService.getAllLibraries()
    }


}