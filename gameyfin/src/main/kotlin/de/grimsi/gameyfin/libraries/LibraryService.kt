package de.grimsi.gameyfin.libraries

import de.grimsi.gameyfin.config.ConfigProperties
import de.grimsi.gameyfin.config.ConfigService
import de.grimsi.gameyfin.games.GameService
import de.grimsi.gameyfin.games.dto.GameDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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

    private val log = KotlinLogging.logger {}

    fun test(testString: String): GameDto {
        val game = gameService.createFromFile(Path(testString))

        val randomLibrary = libraryRepository.findRandomLibrary() ?: throw IllegalArgumentException("No library found")

        randomLibrary.games.add(game)
        libraryRepository.save(randomLibrary)

        return gameService.toDto(game)
    }

    fun createOrUpdate(library: LibraryDto): LibraryDto {
        val entity = libraryRepository.save(toEntity(library))
        return toDto(entity)
    }

    @Transactional(readOnly = true)
    fun getAllLibraries(): Collection<LibraryDto> {
        val entities = libraryRepository.findAll()
        return entities.map { toDto(it) }
    }

    fun deleteLibrary(library: LibraryDto) {
        val entity = toEntity(library)
        libraryRepository.delete(entity)
    }

    fun deleteAllLibraries() {
        libraryRepository.deleteAll()
    }

    @Transactional(readOnly = true)
    fun getGamesInLibrary(libraryId: Long): Collection<GameDto> {
        val library = libraryRepository.findByIdOrNull(libraryId)
            ?: throw IllegalArgumentException("Library with ID $libraryId not found")

        val games = library.games.map { gameService.toDto(it) }

        return games
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
        val validDirectories = library.directories.map { Path(it) }
            .filter { path ->
                if (!path.isDirectory()) {
                    log.warn { "Invalid directory '$path' in library '${library.name}'" }
                    false
                } else {
                    true
                }
            }

        return validDirectories.flatMap { directory ->
            directory.listDirectoryEntries()
                .filter { it.isDirectory() || it.isGameFile() }
                .map { it.fileName }
        }
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

        val statsDto = LibraryStatsDto(
            gamesCount = library.games.size,
            downloadedGamesCount = library.games.sumOf { it.downloadCount }
        )

        return LibraryDto(
            id = library.id,
            name = library.name,
            directories = library.directories,
            stats = statsDto
        )
    }

    private fun toEntity(library: LibraryDto): Library {
        return libraryRepository.findByIdOrNull(library.id) ?: Library(
            name = library.name,
            directories = library.directories
        )
    }
}