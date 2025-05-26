package de.grimsi.gameyfin.libraries

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Role
import de.grimsi.gameyfin.libraries.dto.LibraryDto
import de.grimsi.gameyfin.libraries.dto.LibraryEvent
import de.grimsi.gameyfin.libraries.dto.LibraryUpdateDto
import de.grimsi.gameyfin.libraries.enums.ScanType
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import reactor.core.publisher.Flux

@Endpoint
@PermitAll
class LibraryEndpoint(
    private val libraryService: LibraryService
) {
    fun subscribe(): Flux<List<LibraryEvent>> {
        return LibraryService.subscribe()
    }

    fun getAll() = libraryService.getAll()

    @RolesAllowed(Role.Names.ADMIN)
    fun triggerScan(scanType: ScanType = ScanType.QUICK, libraries: Collection<LibraryDto>?) =
        libraryService.triggerScan(scanType, libraries)

    @RolesAllowed(Role.Names.ADMIN)
    fun createLibrary(library: LibraryDto) = libraryService.create(library)

    @RolesAllowed(Role.Names.ADMIN)
    fun updateLibrary(library: LibraryUpdateDto) = libraryService.update(library)

    @RolesAllowed(Role.Names.ADMIN)
    fun deleteLibrary(libraryId: Long) = libraryService.delete(libraryId)
}