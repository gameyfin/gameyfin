package org.gameyfin.app.libraries

import org.gameyfin.app.core.plugins.PluginService
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.games.GameService
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.libraries.entities.IgnoredPath
import org.gameyfin.app.libraries.entities.IgnoredPathPluginSource
import org.gameyfin.app.libraries.entities.IgnoredPathUserSource
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.users.UserService
import org.gameyfin.pluginapi.gamemetadata.GameMetadataProvider
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Class for shared logic from LibraryService and LibraryScanService.
 */
@Service
class LibraryCoreService(
    private val libraryRepository: LibraryRepository,
    private val gameService: GameService,
    private val ignoredPathRepository: IgnoredPathRepository,
    private val userService: UserService,
    private val pluginService: PluginService
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

        var removedAnyIgnoredPaths = false
        for (game in newGames) {
            val ignoredPath = library.ignoredPaths.find { it.path == game.metadata.path }
            if (ignoredPath != null) {
                library.ignoredPaths.remove(ignoredPath)
                removedAnyIgnoredPaths = true
            }
        }

        if (removedAnyIgnoredPaths || persist) {
            library.updatedAt = Instant.now()
            return libraryRepository.save(library)
        }

        return library
    }

    fun deleteGameFromLibrary(gameId: Long) {
        val game = gameService.getById(gameId)
        val library = game.library

        library.games.removeIf { it.id == gameId }

        // Check if an IgnoredPath with this path already exists
        val existingIgnoredPath = ignoredPathRepository.findByPath(game.metadata.path)

        if (existingIgnoredPath == null) {
            // Create an IgnoredPath with correct source
            val auth = getCurrentAuth()
            val source = if (auth != null) {
                val currentUser = userService.getByUsernameNonNull(auth.name)
                IgnoredPathUserSource(currentUser)
            } else {
                IgnoredPathPluginSource(
                    pluginService.getPluginManagementEntries(GameMetadataProvider::class.java).toMutableList()
                )
            }

            val ignoredPath = IgnoredPath(
                path = game.metadata.path,
                source = source
            )
            library.ignoredPaths.add(ignoredPath)
        } else {
            // Path already exists, just ensure it's in the library's collection if not already
            if (!library.ignoredPaths.any { it.id == existingIgnoredPath.id }) {
                library.ignoredPaths.add(existingIgnoredPath)
            }
        }

        library.updatedAt = Instant.now() // Force the EntityListener to trigger an update and update the timestamp
        libraryRepository.save(library)
    }
}