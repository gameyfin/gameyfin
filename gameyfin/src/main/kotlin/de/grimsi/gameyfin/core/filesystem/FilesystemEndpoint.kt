package de.grimsi.gameyfin.core.filesystem

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Role
import jakarta.annotation.security.RolesAllowed

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class FilesystemEndpoint(
    private val filesystemService: FilesystemService
) {

    fun listContents(path: String) = filesystemService.listContents(path)

    fun listSubDirectories(path: String) = filesystemService.listSubDirectories(path)

    fun getHostOperatingSystem() = filesystemService.getHostOperatingSystem()
}