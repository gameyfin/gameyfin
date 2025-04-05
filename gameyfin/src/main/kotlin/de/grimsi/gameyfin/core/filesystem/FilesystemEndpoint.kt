package de.grimsi.gameyfin.core.filesystem

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Role
import jakarta.annotation.security.RolesAllowed

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class FilesystemEndpoint {
    
    fun listContents(path: String?) = FilesystemService().listContents(path)

    fun listSubDirectories(path: String?) = FilesystemService().listSubDirectories(path)
}