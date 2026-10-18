package org.gameyfin.app.core.plugins.data

import org.gameyfin.app.collections.entities.Collection
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.libraries.entities.Library
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository

/*
 * Lightweight queries for the PluginDataService, which avoid loading the complete (eagerly fetched) entities.
 * Rows are returned as tuples and mapped in the PluginDataService.
 */

private const val GAME_COLUMNS =
    "g.id, g.title, g.library.id, g.metadata.path, g.metadata.fileSize, g.release, g.createdAt, g.updatedAt"

interface PluginGameDataRepository : Repository<Game, Long> {

    @Query("SELECT $GAME_COLUMNS FROM Game g ORDER BY g.id")
    fun findAllRows(): List<Array<Any?>>

    @Query("SELECT $GAME_COLUMNS FROM Game g WHERE g.id IN :ids ORDER BY g.id")
    fun findRowsByIds(ids: kotlin.collections.Collection<Long>): List<Array<Any?>>

    @Query("SELECT $GAME_COLUMNS FROM Game g WHERE g.library.id = :libraryId ORDER BY g.id")
    fun findRowsByLibraryId(libraryId: Long): List<Array<Any?>>

    @Query("SELECT g.id, p FROM Game g JOIN g.platforms p")
    fun findAllPlatforms(): List<Array<Any?>>

    @Query("SELECT g.id, p FROM Game g JOIN g.platforms p WHERE g.id IN :ids")
    fun findPlatformsByIds(ids: kotlin.collections.Collection<Long>): List<Array<Any?>>
}

interface PluginLibraryDataRepository : Repository<Library, Long> {

    @Query("SELECT l.id, l.name FROM Library l ORDER BY l.id")
    fun findAllNames(): List<Array<Any?>>

    @Query("SELECT l.id, d.internalPath FROM Library l JOIN l.directories d")
    fun findAllDirectories(): List<Array<Any?>>

    @Query("SELECT l.id, p FROM Library l JOIN l.platforms p")
    fun findAllPlatforms(): List<Array<Any?>>
}

interface PluginCollectionDataRepository : Repository<Collection, Long> {

    @Query("SELECT c.id, c.name, c.description FROM Collection c ORDER BY c.id")
    fun findAllRows(): List<Array<Any?>>

    @Query("SELECT c.id, g.id FROM Collection c JOIN c.games g")
    fun findAllGameIds(): List<Array<Any?>>
}
