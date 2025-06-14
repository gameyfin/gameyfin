package org.gameyfin.app.core.filesystem

import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class FilesystemEndpoint(
    private val filesystemService: FilesystemService
) {

    fun listContents(path: String) = filesystemService.listContents(path)

    fun listSubDirectories(path: String) = filesystemService.listSubDirectories(path)

    fun getHostOperatingSystem() = filesystemService.getHostOperatingSystem()
}