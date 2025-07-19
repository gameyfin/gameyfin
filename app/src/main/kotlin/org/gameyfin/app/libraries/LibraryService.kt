package org.gameyfin.app.libraries

import com.vaadin.hilla.exception.EndpointException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.games.GameService
import org.gameyfin.app.libraries.dto.LibraryDto
import org.gameyfin.app.libraries.dto.LibraryEvent
import org.gameyfin.app.libraries.dto.LibraryUpdateDto
import org.gameyfin.app.libraries.enums.ScanType
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
    private val libraryCoreService: LibraryCoreService,
    private val libraryScanService: LibraryScanService,
    private val gameService: GameService
) {

    companion object {
        private val log = KotlinLogging.logger {}

        /* Websockets */
        private val libraryEvents = Sinks.many().multicast().onBackpressureBuffer<LibraryEvent>(1024, false)

        fun subscribeToLibraryEvents(): Flux<List<LibraryEvent>> {
            log.debug { "New subscription for libraryEvents" }
            return libraryEvents.asFlux()
                .buffer(100.milliseconds.toJavaDuration())
                .doOnSubscribe {
                    log.debug { "Subscriber added to libraryEvents [${libraryEvents.currentSubscriberCount()}]" }
                }
                .doFinally {
                    log.debug { "Subscriber removed from libraryEvents with signal type $it [${libraryEvents.currentSubscriberCount()}]" }
                }
        }

        fun emit(event: LibraryEvent) {
            libraryEvents.tryEmitNext(event)
        }
    }


    /**
     * Retrieves all libraries from the repository.
     */
    fun getAll(): List<LibraryDto> {
        val entities = libraryRepository.findAll()
        return entities.map { it.toDto() }
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
    fun create(library: LibraryDto, scanAfterCreation: Boolean) {
        // Check for duplicate directories before creating a new library
        checkForDuplicateDirectories(library.directories.map { it.internalPath })

        val newLibrary = libraryRepository.save(libraryCoreService.toEntity(library))

        if (scanAfterCreation) {
            libraryScanService.triggerScan(ScanType.QUICK, listOf(newLibrary.toDto()))
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
        libraryUpdateDto.unmatchedPaths?.let {
            library.unmatchedPaths.clear()
            library.unmatchedPaths.addAll(it)
        }

        library.updatedAt = Instant.now()
        libraryRepository.save(library)
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