package org.gameyfin.app.libraries

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.annotations.DynamicPublicAccess
import org.gameyfin.app.core.security.isCurrentUserAdmin
import org.gameyfin.app.libraries.dto.LibraryAdminDto
import org.gameyfin.app.libraries.dto.LibraryEvent
import org.gameyfin.app.libraries.dto.LibraryScanProgress
import org.gameyfin.app.libraries.dto.LibraryUpdateDto
import org.gameyfin.app.libraries.enums.ScanType
import reactor.core.publisher.Flux

@Endpoint
@DynamicPublicAccess
@AnonymousAllowed
class LibraryEndpoint(
    private val libraryService: LibraryService,
    private val libraryScanService: LibraryScanService,
) {
    fun subscribeToLibraryEvents(): Flux<List<LibraryEvent>> {
        return LibraryService.subscribeToLibraryEvents()
    }

    fun getAll() = libraryService.getAll()

    fun subscribeToScanProgressEvents(): Flux<List<LibraryScanProgress>> {
        return if (isCurrentUserAdmin()) LibraryScanService.subscribeToScanProgressEvents()
        else Flux.empty()
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun triggerScan(scanType: ScanType = ScanType.QUICK, libraryIds: Collection<Long>?) =
        libraryScanService.triggerScan(scanType, libraryIds)

    @RolesAllowed(Role.Names.ADMIN)
    fun createLibrary(library: LibraryAdminDto, scanAfterCreation: Boolean = true) =
        libraryService.create(library, scanAfterCreation)

    @RolesAllowed(Role.Names.ADMIN)
    fun updateLibrary(library: LibraryUpdateDto) = libraryService.update(library)

    @RolesAllowed(Role.Names.ADMIN)
    fun deleteLibrary(libraryId: Long) = libraryService.delete(libraryId)
}