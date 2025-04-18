package de.grimsi.gameyfin.libraries

import de.grimsi.gameyfin.core.filesystem.FilesystemService
import de.grimsi.gameyfin.games.GameService
import de.grimsi.gameyfin.games.dto.GameDto
import de.grimsi.gameyfin.games.entities.Game
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class LibraryService(
    private val libraryRepository: LibraryRepository,
    private val gameService: GameService,
    private val filesystemService: FilesystemService
) {

    private val log = KotlinLogging.logger {}

    /**
     * Creates or updates a library in the repository.
     *
     * @param library: The library to create or update.
     * @return The created or updated LibraryDto object.
     */
    fun createOrUpdate(library: LibraryDto): LibraryDto {
        val entity = libraryRepository.save(toEntity(library))
        return toDto(entity)
    }

    /**
     * Retrieves all libraries from the repository.
     */
    fun getAllLibraries(): Collection<LibraryDto> {
        val entities = libraryRepository.findAll()
        return entities.map { toDto(it) }
    }

    /**
     * Deletes a library from the repository.
     *
     * @param library: The library to delete.
     */
    fun deleteLibrary(library: LibraryDto) {
        val entity = toEntity(library)
        libraryRepository.delete(entity)
    }

    /**
     * Deletes all libraries from the repository.
     */
    fun deleteAllLibraries() {
        libraryRepository.deleteAll()
    }

    /**
     * Retrieves all games in a library.
     *
     * @param libraryId: The ID of the library to retrieve games from.
     * @return A collection of GameDto objects representing the games in the library.
     */
    fun getGamesInLibrary(libraryId: Long): Collection<GameDto> {
        val library = libraryRepository.findByIdOrNull(libraryId)
            ?: throw IllegalArgumentException("Library with ID $libraryId not found")

        val games = library.games.map { gameService.toDto(it) }

        return games
    }

    /**
     * Adds a game to the library.
     *
     * @param game: The game to add.
     * @param library: The library to add the game to.
     * @return The updated library.
     */
    fun addGameToLibrary(game: Game, library: Library): Library {
        if (library.games.any { it.id == game.id }) return library

        library.games.add(game)
        return libraryRepository.save(library)
    }

    /**
     * Adds a collection of games to the library.
     *
     * @param games: The collection of games to add.
     * @param library: The library to add the games to.
     * @return The updated library.
     */
    fun addGamesToLibrary(games: Collection<Game>, library: Library): Library {
        val newGames = games.filter { game -> library.games.none { it.id == game.id } }
        library.games = library.games.toMutableSet().apply { addAll(newGames) }
        return libraryRepository.save(library)
    }


    /**
     * Wrapper function to trigger a scan for a list of libraries.
     */
    fun triggerScan(libraryDtos: Collection<LibraryDto>?) = runBlocking {
        scan(libraryDtos)
    }

    /**
     * Triggers a scan for a list of libraries.
     * If no list is provided, all libraries will be scanned.
     *
     * @param libraryDtos: List of LibraryDto objects to scan.
     */
    suspend fun scan(libraryDtos: Collection<LibraryDto>?) {
        val libraries = libraryDtos?.map { toEntity(it) } ?: libraryRepository.findAll()
        libraries.forEach { library ->
            val gamePaths = filesystemService.scanLibraryForGamefiles(library)
            val newGames = gamePaths.map { gameService.createFromFile(it) }
            addGamesToLibrary(newGames, library)
        }
    }

    /**
     * Converts a Library entity to a LibraryDto.
     *
     * @param library: The Library entity to convert.
     * @return The converted LibraryDto.
     */
    private fun toDto(library: Library): LibraryDto {
        val libraryId = library.id ?: throw IllegalArgumentException("Library ID is null")

        val statsDto = LibraryStatsDto(
            gamesCount = library.games.size,
            downloadedGamesCount = library.games.sumOf { it.downloadCount }
        )

        return LibraryDto(
            id = libraryId,
            name = library.name,
            directories = library.directories,
            stats = statsDto
        )
    }

    /**
     * Converts a LibraryDto to a Library entity.
     *
     * @param library: The LibraryDto to convert.
     * @return The converted Library entity.
     */
    private fun toEntity(library: LibraryDto): Library {
        return libraryRepository.findByIdOrNull(library.id) ?: Library(
            name = library.name,
            directories = library.directories.toMutableSet()
        )
    }
}