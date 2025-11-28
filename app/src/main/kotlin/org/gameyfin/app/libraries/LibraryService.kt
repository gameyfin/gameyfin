package org.gameyfin.app.libraries

import com.vaadin.hilla.exception.EndpointException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.libraries.dto.*
import org.gameyfin.app.libraries.entities.DirectoryMapping
import org.gameyfin.app.libraries.entities.IgnoredPathSourceType
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.libraries.enums.ScanType
import org.gameyfin.app.libraries.extensions.toDtos
import org.gameyfin.app.libraries.extensions.toEntity
import org.gameyfin.app.users.UserService
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

@Service
class LibraryService(
    private val libraryRepository: LibraryRepository,
    private val libraryScanService: LibraryScanService,
    private val userService: UserService,
    private val ignoredPathRepository: IgnoredPathRepository,
) {

    companion object {
        private val log = KotlinLogging.logger {}

        /* Websockets */
        private val libraryUserEvents = Sinks.many().multicast().onBackpressureBuffer<LibraryUserEvent>(1024, false)
        private val libraryAdminEvents = Sinks.many().multicast().onBackpressureBuffer<LibraryAdminEvent>(1024, false)

        fun subscribeUser(): Flux<List<LibraryUserEvent>> {
            log.debug { "New user subscription for libraryEvents" }
            return libraryUserEvents.asFlux()
                .buffer(100.milliseconds.toJavaDuration())
                .doOnSubscribe {
                    log.debug { "Subscriber added to user libraryUserEvents [${libraryUserEvents.currentSubscriberCount()}]" }
                }
                .doFinally {
                    log.debug { "Subscriber removed from user libraryUserEvents with signal type $it [${libraryUserEvents.currentSubscriberCount()}]" }
                }
        }

        fun subscribeAdmin(): Flux<List<LibraryAdminEvent>> {
            log.debug { "New admin subscription for libraryEvents" }
            return libraryAdminEvents.asFlux()
                .buffer(100.milliseconds.toJavaDuration())
                .doOnSubscribe {
                    log.debug { "Subscriber added to admin libraryAdminEvents [${libraryAdminEvents.currentSubscriberCount()}]" }
                }
                .doFinally {
                    log.debug { "Subscriber removed from admin libraryAdminEvents with signal type $it [${libraryAdminEvents.currentSubscriberCount()}]" }
                }
        }

        fun emitUser(event: LibraryUserEvent) {
            libraryUserEvents.tryEmitNext(event)
        }

        fun emitAdmin(event: LibraryAdminEvent) {
            libraryAdminEvents.tryEmitNext(event)
        }
    }


    /**
     * Retrieves all libraries from the repository.
     */
    fun getAll(): List<LibraryDto> {
        val entities = libraryRepository.findAll()
        return entities.toDtos()
    }

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
    }

    /**
     * Updates a library entity with the non-null fields from a LibraryUpdateDto.
     *
     * @param libraryUpdateDto: The LibraryUpdateDto containing the fields to update.
     * @return The updated LibraryDto.
     * @throws IllegalArgumentException if the library ID is null or the library is not found.
     */
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

                // Remove existing USER-sourced ignored paths, keep PLUGIN-sourced ones intact
                library.ignoredPaths.removeIf { it.getType() == IgnoredPathSourceType.USER }

                // Recreate user-sourced paths (reuse existing entity if same path already present globally)
                val pathsToAdd = dtos.map { dto ->
                    val existingPath = ignoredPathRepository.findByPath(dto.path)
                    existingPath ?: dto.toEntity(currentUser)
                }

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