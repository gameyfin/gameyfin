package org.gameyfin.app.libraries

import org.gameyfin.app.games.GameService
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.libraries.dto.DirectoryMappingDto
import org.gameyfin.app.libraries.dto.LibraryDto
import org.gameyfin.app.libraries.dto.LibraryStatsDto
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Class for shared logic from LibraryService and LibraryScanService.
 */
@Service
class LibraryCoreService(
    private val libraryRepository: LibraryRepository,
    private val gameService: GameService,
) {
    /**
     * Adds a collection of games to the library.
     *
     * @param games: The collection of games to add.
     * @param library: The library to add the games to.
     * @return The updated library.
     */
    fun addGamesToLibrary(games: Collection<Game>, library: Library, persist: Boolean = false): Library {
        val newGames = games.filter { game -> library.games.none { it.id == game.id } }
        library.games.addAll(newGames)

        var removedAnyUnmatchedPaths = false
        for (game in newGames) {
            if (library.unmatchedPaths.contains(game.metadata.path)) {
                library.unmatchedPaths.remove(game.metadata.path)
                removedAnyUnmatchedPaths = true
            }
        }

        if (removedAnyUnmatchedPaths || persist) {
            library.updatedAt = Instant.now()
            return libraryRepository.save(library)
        }

        return library
    }

    fun deleteGameFromLibrary(gameId: Long) {
        val game = gameService.getById(gameId)
        val library = game.library

        library.games.removeIf { it.id == gameId }
        library.unmatchedPaths.add(game.metadata.path)

        library.updatedAt = Instant.now() // Force the EntityListener to trigger an update and update the timestamp
        libraryRepository.save(library)
    }


    /**
     * Converts a LibraryDto to a Library entity.
     *
     * @param library: The LibraryDto to convert.
     * @return The converted Library entity.
     */
    fun toEntity(library: LibraryDto): Library {
        return libraryRepository.findByIdOrNull(library.id) ?: Library(
            name = library.name,
            directories = library.directories.distinctBy { it.internalPath }.map {
                DirectoryMapping(internalPath = it.internalPath, externalPath = it.externalPath)
            }.toMutableList(),
        )
    }
}

fun Library.toDto(): LibraryDto {
    val statsDto = LibraryStatsDto(
        gamesCount = this.games.size,
        downloadedGamesCount = this.games.sumOf { it.metadata.downloadCount }
    )

    return LibraryDto(
        id = this.id!!,
        name = this.name,
        directories = this.directories.map { DirectoryMappingDto(it.internalPath, it.externalPath) },
        games = this.games.mapNotNull { it.id },
        stats = statsDto,
        unmatchedPaths = this.unmatchedPaths
    )
}