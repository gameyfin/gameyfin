package org.gameyfin.app.core.plugins.data

import org.gameyfin.pluginapi.data.CollectionInfo
import org.gameyfin.pluginapi.data.GameInfo
import org.gameyfin.pluginapi.data.GameyfinDataService
import org.gameyfin.pluginapi.data.LibraryInfo
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Path
import java.time.Instant

/**
 * Read-only access to games, libraries and collections for plugins (see [GameyfinDataService]).
 * Provided to every plugin via its [org.gameyfin.app.core.plugins.management.GameyfinPluginContext].
 */
@Service
@Transactional(readOnly = true)
class PluginDataService(
    private val gameRepository: PluginGameDataRepository,
    private val libraryRepository: PluginLibraryDataRepository,
    private val collectionRepository: PluginCollectionDataRepository
) : GameyfinDataService {

    override fun getGames(): List<GameInfo> =
        toGames(gameRepository.findAllRows(), groupById(gameRepository.findAllPlatforms()))

    override fun getGames(ids: Collection<Long>): List<GameInfo> {
        if (ids.isEmpty()) return emptyList()
        val distinctIds = ids.toSet()
        return toGames(gameRepository.findRowsByIds(distinctIds), groupById(gameRepository.findPlatformsByIds(distinctIds)))
    }

    override fun getGame(id: Long): GameInfo? = getGames(listOf(id)).firstOrNull()

    override fun getGamesOfLibrary(libraryId: Long): List<GameInfo> {
        val rows = gameRepository.findRowsByLibraryId(libraryId)
        if (rows.isEmpty()) return emptyList()
        val platforms = groupById<Platform>(gameRepository.findPlatformsByIds(rows.map { it[0] as Long }))
        return toGames(rows, platforms)
    }

    override fun getLibraries(): List<LibraryInfo> {
        val directories = groupById<String>(libraryRepository.findAllDirectories())
        val platforms = groupById<Platform>(libraryRepository.findAllPlatforms())
        return libraryRepository.findAllNames().map { row ->
            val id = row[0] as Long
            LibraryInfo(
                id = id,
                name = row[1] as String,
                directories = directories[id].orEmpty().map { Path.of(it) },
                platforms = platforms[id].orEmpty()
            )
        }
    }

    override fun getLibrary(id: Long): LibraryInfo? = getLibraries().find { it.id == id }

    override fun getCollections(): List<CollectionInfo> {
        val gameIds = groupById<Long>(collectionRepository.findAllGameIds())
        return collectionRepository.findAllRows().map { row ->
            val id = row[0] as Long
            CollectionInfo(
                id = id,
                name = row[1] as String,
                description = row[2] as String?,
                gameIds = gameIds[id].orEmpty().sorted()
            )
        }
    }

    override fun getCollection(id: Long): CollectionInfo? = getCollections().find { it.id == id }

    private fun toGames(rows: List<Array<Any?>>, platforms: Map<Long, List<Platform>>): List<GameInfo> =
        rows.map { row ->
            val id = row[0] as Long
            GameInfo(
                id = id,
                title = row[1] as String?,
                libraryId = row[2] as Long,
                path = Path.of(row[3] as String),
                fileSize = row[4] as Long?,
                platforms = platforms[id].orEmpty(),
                release = row[5] as Instant?,
                createdAt = row[6] as Instant?,
                updatedAt = row[7] as Instant?
            )
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T> groupById(rows: List<Array<Any?>>): Map<Long, List<T>> =
        rows.groupBy({ it[0] as Long }, { it[1] as T })
}
