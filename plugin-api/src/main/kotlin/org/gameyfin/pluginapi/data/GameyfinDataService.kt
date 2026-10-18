package org.gameyfin.pluginapi.data

import org.gameyfin.pluginapi.gamemetadata.Platform
import java.nio.file.Path
import java.time.Instant

/**
 * Read-only access to the most important data of Gameyfin (games, libraries and collections).
 *
 * Available to every plugin via [org.gameyfin.pluginapi.core.wrapper.PluginContext.dataService]
 * (or [org.gameyfin.pluginapi.core.wrapper.GameyfinPlugin.dataService]). All methods return snapshots, changes made by Gameyfin afterward are not reflected.
 */
interface GameyfinDataService {

    /**
     * Returns all games of all libraries.
     */
    fun getGames(): List<GameInfo>

    /**
     * Returns the games with the given IDs. Unknown IDs are ignored.
     */
    fun getGames(ids: Collection<Long>): List<GameInfo>

    /**
     * Returns the game with the given ID, or `null` if it does not exist.
     */
    fun getGame(id: Long): GameInfo?

    /**
     * Returns the games of the library with the given ID.
     */
    fun getGamesOfLibrary(libraryId: Long): List<GameInfo>

    /**
     * Returns all libraries.
     */
    fun getLibraries(): List<LibraryInfo>

    /**
     * Returns the library with the given ID, or `null` if it does not exist.
     */
    fun getLibrary(id: Long): LibraryInfo?

    /**
     * Returns all collections.
     */
    fun getCollections(): List<CollectionInfo>

    /**
     * Returns the collection with the given ID, or `null` if it does not exist.
     */
    fun getCollection(id: Long): CollectionInfo?
}

/**
 * A game managed by Gameyfin.
 *
 * @property path Path of the game file or directory, as passed to
 * [org.gameyfin.pluginapi.download.DownloadProvider.download]
 * @property fileSize Size of the game in bytes, if known
 */
data class GameInfo(
    val id: Long,
    val title: String?,
    val libraryId: Long,
    val path: Path,
    val fileSize: Long?,
    val platforms: List<Platform>,
    val release: Instant?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)

/**
 * A library of games.
 *
 * @property directories Directories (as seen by Gameyfin) which are scanned for games
 */
data class LibraryInfo(
    val id: Long,
    val name: String,
    val directories: List<Path>,
    val platforms: List<Platform>
)

/**
 * A collection of games.
 */
data class CollectionInfo(
    val id: Long,
    val name: String,
    val description: String?,
    val gameIds: List<Long>
)
