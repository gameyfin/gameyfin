package de.grimsi.gameyfin.libraries

import de.grimsi.gameyfin.libraries.entities.Library
import de.grimsi.gameyfin.libraries.persistence.LibraryRepository
import org.springframework.stereotype.Service

@Service
class LibraryService(
    private val libraryRepository: LibraryRepository
) {
    fun createLibrary(library: Library): Library {
        return libraryRepository.save(library)
    }

    fun getAllLibraries(): Collection<Library> {
        return libraryRepository.findAll()
    }

    fun deleteLibrary(library: Library) {
        libraryRepository.delete(library)
    }

    fun updateLibrary(library: Library) {
        libraryRepository.save(library)
    }
}