package de.grimsi.gameyfin.libraries

import de.grimsi.gameyfin.config.ConfigProperties
import de.grimsi.gameyfin.config.ConfigService
import de.grimsi.gameyfin.games.GameService
import de.grimsi.gameyfin.games.dto.GameDto
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

@Service
class LibraryService(
    private val libraryRepository: LibraryRepository,
    private val gameService: GameService,
    private val config: ConfigService
) {
    fun test(testString: String): GameDto {
        return gameService.createFromFile(Path(testString))
    }

    fun createOrUpdate(library: LibraryDto): LibraryDto {
        val entity = libraryRepository.save(toEntity(library))
        return toDto(entity)
    }

    fun getAllLibraries(): Collection<LibraryDto> {
        val entities = libraryRepository.findAll()
        return entities.map { toDto(it) }
    }

    fun deleteLibrary(library: LibraryDto) {
        val entity = toEntity(library)
        libraryRepository.delete(entity)
    }

    /**
     * Triggers a scan for a list of libraries. If no list is provided, all libraries will be scanned.
     */
    fun scan(libraryDtos: Collection<LibraryDto>?) {
        val libraries = libraryDtos?.map { toEntity(it) } ?: libraryRepository.findAll()
        libraries.forEach { library ->
            val games = scan(library)
            games.forEach(gameService::createFromFile)
        }
    }

    /**
     * Return a list of all subfolders and game files in the provided library
     */
    fun scan(library: Library): List<Path> {
        val folder = Path(library.path)
        if (!folder.isDirectory()) throw IllegalArgumentException("The provided path is not a valid directory")
        return folder
            .listDirectoryEntries()
            .filter { it.isDirectory() || it.isGameFile() }
            .map { it.fileName }
    }

    private fun Path.isGameFile(): Boolean {
        val gameFileExtensions = config.get(ConfigProperties.Libraries.Scan.GameFileExtensions)!!
            .split(",")
            .map { it.trim().lowercase() }
        return extension.lowercase() in gameFileExtensions
    }

    private fun toDto(library: Library): LibraryDto {
        if (library.id == null) {
            throw IllegalArgumentException("Library ID is null")
        }

        return LibraryDto(
            id = library.id,
            name = library.name,
            path = library.path
        )
    }

    private fun toEntity(library: LibraryDto): Library {
        return libraryRepository.findByIdOrNull(library.id) ?: Library(
            id = library.id,
            name = library.name,
            path = library.path
        )
    }
}