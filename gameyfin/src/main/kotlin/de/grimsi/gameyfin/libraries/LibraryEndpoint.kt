package de.grimsi.gameyfin.libraries

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Role
import de.grimsi.gameyfin.libraries.dto.LibraryDto
import de.grimsi.gameyfin.libraries.dto.LibraryEvent
import de.grimsi.gameyfin.libraries.dto.LibraryScanProgress
import de.grimsi.gameyfin.libraries.dto.LibraryUpdateDto
import de.grimsi.gameyfin.libraries.enums.ScanType
import de.grimsi.gameyfin.users.util.isAdmin
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import reactor.core.publisher.Flux

@Endpoint
@PermitAll
class LibraryEndpoint(
    private val libraryService: LibraryService
) {
    fun subscribeToLibraryEvents(): Flux<List<LibraryEvent>> {
        return LibraryService.subscribeToLibraryEvents()
    }

    fun getAll() = libraryService.getAll()


    fun subscribeToScanProgressEvents(): Flux<List<LibraryScanProgress>> {
        val user = SecurityContextHolder.getContext().authentication.principal as UserDetails
        return if (user.isAdmin()) LibraryService.subscribeToScanProgressEvents()
        else Flux.empty()
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun triggerScan(scanType: ScanType = ScanType.QUICK, libraries: Collection<LibraryDto>?) =
        libraryService.triggerScan(scanType, libraries)

    @RolesAllowed(Role.Names.ADMIN)
    fun createLibrary(library: LibraryDto, scanAfterCreation: Boolean = true) =
        libraryService.create(library, scanAfterCreation)

    @RolesAllowed(Role.Names.ADMIN)
    fun updateLibrary(library: LibraryUpdateDto) = libraryService.update(library)

    @RolesAllowed(Role.Names.ADMIN)
    fun deleteLibrary(libraryId: Long) = libraryService.delete(libraryId)
}