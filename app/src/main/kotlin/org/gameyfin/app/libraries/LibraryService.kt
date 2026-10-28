package org.gameyfin.app.libraries

import com.vaadin.hilla.exception.EndpointException
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.core.sync.EntityEventBroadcaster
import org.gameyfin.app.core.sync.SYNC_SAFETY_WINDOW
import org.gameyfin.app.libraries.dto.*
import org.gameyfin.app.libraries.entities.DirectoryMapping
import org.gameyfin.app.libraries.entities.IgnoredPathSourceType
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.libraries.enums.ScanType
import org.gameyfin.app.libraries.extensions.toAdminDto
import org.gameyfin.app.libraries.extensions.toEntity
import org.gameyfin.app.libraries.extensions.toSummaryDto
import org.gameyfin.app.users.UserService
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import java.time.Instant

@Service
class LibraryService(
    private val libraryRepository: LibraryRepository,
    private val libraryScanService: LibraryScanService,
    private val userService: UserService,
    private val ignoredPathRepository: IgnoredPathRepository,
) {

    companion object {
        /* Websockets */
        private val userEvents = EntityEventBroadcaster<LibraryEvent>("libraryUserEvents")
        private val adminEvents = EntityEventBroadcaster<LibraryEvent>("libraryAdminEvents")

        fun subscribeUser(): Flux<List<LibraryEvent>> = userEvents.subscribe()

        fun subscribeAdmin(): Flux<List<LibraryEvent>> = adminEvents.subscribe()

        fun emitUser(event: LibraryEvent) = userEvents.emit(event)

        fun emitAdmin(event: LibraryEvent) = adminEvents.emit(event)
    }

    /**
     * Returns the library summaries that changed since the given timestamp, or all if [since] is `null`.
     * Clients have to remove all libraries that are not contained in [LibrarySyncDto.allIds] (delta sync only).
     */
    @Transactional(readOnly = true)
    fun sync(since: Instant?, admin: Boolean): LibrarySyncDto {
        val serverTime = Instant.now()

        if (since == null) {
            return LibrarySyncDto(
                serverTime = serverTime,
                full = true,
                libraries = libraryRepository.findAll().map { it.toSummaryDto(admin) },
                allIds = emptyList()
            )
        }

        return LibrarySyncDto(
            serverTime = serverTime,
            full = false,
            libraries = libraryRepository.findAllByUpdatedAtAfter(since.minus(SYNC_SAFETY_WINDOW))
                .map { it.toSummaryDto(admin) },
            allIds = libraryRepository.findAllIds()
        )
    }

    /**
     * Returns the full details of a library, `null` if it does not exist.
     */
    @Transactional(readOnly = true)
    fun getAdminDtoById(libraryId: Long): LibraryAdminDto? = libraryRepository.findByIdOrNull(libraryId)?.toAdminDto()

    /**
     * Retrieves a library by its ID.
     */
    fun getById(libraryId: Long): Library {
        val library = libraryRepository.findByIdOrNull(libraryId)
            ?: throw IllegalArgumentException("Library with ID $libraryId not found")
        return library
    }

    /**
     * Creates or updates a library in the repository.
     *
     * @param library: The library to create or update.
     * @return The created or updated LibraryDto object.
     */
    fun create(library: LibraryAdminDto, scanAfterCreation: Boolean) {
        createEntity(library, scanAfterCreation)
    }

    fun createReturningId(library: LibraryAdminDto, scanAfterCreation: Boolean): Long =
        createEntity(library, scanAfterCreation).id!!

    private fun createEntity(library: LibraryAdminDto, scanAfterCreation: Boolean): Library {
        // Check for duplicate directories before creating a new library
        checkForDuplicateDirectories(library.directories.map { it.internalPath })

        val directories = library.directories.distinctBy { it.internalPath }.map {
            DirectoryMapping(internalPath = it.internalPath, externalPath = it.externalPath)
        }.toMutableList()

        var newLibrary = Library(
            name = library.name,
            directories = directories,
            platforms = library.platforms.toMutableList()
        )

        newLibrary = libraryRepository.save(newLibrary)

        if (scanAfterCreation) {
            libraryScanService.triggerScan(ScanType.QUICK, listOf(newLibrary.id!!))
        }
        return newLibrary
    }

    /**
     * Updates a library entity with the non-null fields from a LibraryUpdateDto.
     *
     * @param libraryUpdateDto: The LibraryUpdateDto containing the fields to update.
     * @return The updated LibraryDto.
     * @throws IllegalArgumentException if the library ID is null or the library is not found.
     */
    @Transactional
    fun update(libraryUpdateDto: LibraryUpdateDto) {
        val library = libraryRepository.findByIdOrNull(libraryUpdateDto.id)
            ?: throw IllegalArgumentException("Library with ID $libraryUpdateDto.id not found")

        libraryUpdateDto.name?.let { library.name = it }

        libraryUpdateDto.directories?.let { updatedDirs ->
            checkForDuplicateDirectories(
                updatedDirs.map { it.internalPath },
                excludeLibraryId = library.id
            )

            val existingMappings = library.directories.associateBy { it.internalPath }
            val updatedInternalPaths = updatedDirs.map { it.internalPath }.toSet()

            // Remove mappings not present in the update
            val removedDirs = library.directories.filter { it.internalPath !in updatedInternalPaths }
            library.directories.removeAll(removedDirs)

            // Remove all games within removed directories
            val removedDirPaths = removedDirs.map { it.internalPath }
            library.games.removeIf { game ->
                removedDirPaths.any { removedPath ->
                    game.metadata.path.startsWith(removedPath)
                }
            }

            // Update existing or add new directory mappings
            updatedDirs.forEach { dto ->
                val mapping = existingMappings[dto.internalPath]
                if (mapping != null) {
                    mapping.externalPath = dto.externalPath // update fields
                } else {
                    library.directories.add(
                        DirectoryMapping(
                            internalPath = dto.internalPath,
                            externalPath = dto.externalPath
                        )
                    )
                }
            }
        }

        libraryUpdateDto.platforms?.let {
            library.platforms.clear()
            library.platforms.addAll(it)
        }

        // Only allow updating USER sourced ignored paths; preserve PLUGIN sourced ones
        libraryUpdateDto.ignoredPaths
            ?.filter { it.sourceType == IgnoredPathSourceTypeDto.USER } // Only USER source type is supported for updates
            ?.let { dtos ->
                val currentUser = getCurrentAuth()?.let { auth -> userService.getByUsername(auth.name) }

                // Diff against the current state instead of clearing and re-adding, which conflicts with orphanRemoval
                val requestedPaths = dtos.map { it.path }.toSet()
                library.ignoredPaths.removeIf {
                    it.getType() == IgnoredPathSourceType.USER && it.path !in requestedPaths
                }

                val currentPaths = library.ignoredPaths.map { it.path }.toSet()
                val pathsToAdd = dtos
                    .filter { it.path !in currentPaths }
                    .map { dto -> ignoredPathRepository.findByPath(dto.path) ?: dto.toEntity(currentUser) }

                library.ignoredPaths.addAll(pathsToAdd)
            }

        libraryUpdateDto.metadata?.let {
            library.metadata = it.toEntity()
        }

        library.updatedAt = Instant.now()
        libraryRepository.save(library)
    }

    /**
     * Updates multiple libraries in the repository.
     */
    @Transactional
    fun update(libraries: Collection<LibraryUpdateDto>) {
        libraries.forEach { update(it) }
    }

    /**
     * Deletes a library from the repository.
     *
     * @param libraryId: ID of the library to delete.
     */
    fun delete(libraryId: Long) {
        libraryRepository.deleteById(libraryId)
    }

    private fun checkForDuplicateDirectories(newLibraryFolders: List<String>, excludeLibraryId: Long? = null) {
        val alreadyConfiguredFolders = libraryRepository.findByPaths(newLibraryFolders)
            .filter { it.second.id != excludeLibraryId } // Exclude the current library if updating
            .map { Pair(it.first, it.second) } // Convert to Pair for easier error message formatting

        if (alreadyConfiguredFolders.isNotEmpty()) {
            throw EndpointException(
                "The following directories are already mapped to another library: " +
                        alreadyConfiguredFolders.joinToString(", ") { "${it.first} (${it.second.name})" }
            )
        }
    }
}
