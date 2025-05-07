package de.grimsi.gameyfin.libraries

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Role
import de.grimsi.gameyfin.games.GameService
import de.grimsi.gameyfin.games.dto.GameDto
import de.grimsi.gameyfin.libraries.dto.LibraryDto
import de.grimsi.gameyfin.libraries.dto.LibraryUpdateDto
import de.grimsi.gameyfin.libraries.enums.ScanType
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed

@Endpoint
@PermitAll
class LibraryEndpoint(
    private val libraryService: LibraryService,
    private val gameService: GameService
) {
    fun getAllLibraries(): Collection<LibraryDto> {
        return libraryService.getAllLibraries()
    }

    fun getGamesInLibrary(libraryId: Long): Collection<GameDto> {
        return libraryService.getGamesInLibrary(libraryId)
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun triggerScan(scanType: ScanType = ScanType.QUICK, libraries: Collection<LibraryDto>?) {
        return libraryService.triggerScan(scanType, libraries)
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun createLibrary(library: LibraryDto): LibraryDto {
        return libraryService.create(library)
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun updateLibrary(library: LibraryUpdateDto): LibraryDto {
        return libraryService.update(library)
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun removeLibrary(libraryId: Long) {
        return libraryService.deleteLibrary(libraryId)
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun removeLibraries() {
        return libraryService.deleteAllLibraries()
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun removeGames() {
        return gameService.deleteAll()
    }
}