package org.gameyfin.app.libraries

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.annotations.DynamicPublicAccess
import org.gameyfin.app.core.security.isCurrentUserAdmin
import org.gameyfin.app.core.tasks.DeletionTaskService
import org.gameyfin.app.libraries.dto.LibraryAdminDto
import org.gameyfin.app.libraries.dto.LibraryEvent
import org.gameyfin.app.libraries.dto.LibrarySyncDto
import org.gameyfin.app.libraries.dto.LibraryUpdateDto
import org.gameyfin.app.libraries.enums.ScanType
import reactor.core.publisher.Flux
import java.time.Instant

@Endpoint
@DynamicPublicAccess
@AnonymousAllowed
class LibraryEndpoint(
    private val libraryService: LibraryService,
    private val libraryScanService: LibraryScanService,
    private val deletionTaskService: DeletionTaskService,
) {
    fun subscribeToLibraryEvents(): Flux<out List<LibraryEvent>> {
        return if (isCurrentUserAdmin()) {
            LibraryService.subscribeAdmin()
        } else {
            LibraryService.subscribeUser()
        }
    }

    /**
     * Delta synchronization of the library summaries.
     *
     * @param since `serverTime` of the last synchronization, `null` to fetch all libraries
     */
    fun sync(since: Instant?): LibrarySyncDto = libraryService.sync(since, isCurrentUserAdmin())

    /**
     * Returns the full details of a library, `null` if it does not exist.
     */
    @RolesAllowed(Role.Names.ADMIN)
    fun getById(libraryId: Long): LibraryAdminDto? = libraryService.getAdminDtoById(libraryId)

    @RolesAllowed(Role.Names.ADMIN)
    fun triggerScan(scanType: ScanType = ScanType.QUICK, libraryIds: Collection<Long>?) =
        libraryScanService.triggerScan(scanType, libraryIds)

    @RolesAllowed(Role.Names.ADMIN)
    fun createLibrary(library: LibraryAdminDto, scanAfterCreation: Boolean = true) =
        libraryService.create(library, scanAfterCreation)

    @RolesAllowed(Role.Names.ADMIN)
    fun updateLibrary(library: LibraryUpdateDto) = libraryService.update(library)

    @RolesAllowed(Role.Names.ADMIN)
    fun updateLibraries(libraries: Collection<LibraryUpdateDto>) = libraryService.update(libraries)

    @RolesAllowed(Role.Names.ADMIN)
    fun deleteLibrary(libraryId: Long) = deletionTaskService.deleteLibrary(libraryId)
}
