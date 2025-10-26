package org.gameyfin.app.games.repositories

import org.gameyfin.app.games.entities.Game
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface GameRepository : JpaRepository<Game, Long> {
    @Query("SELECT g FROM Game g WHERE g.title = :title AND YEAR(g.release) = YEAR(:release)")
    fun findByTitleAndReleaseYear(
        @Param("title") title: String,
        @Param("release") release: Instant?
    ): List<Game>

    @Query("SELECT CASE WHEN COUNT(g) > 0 THEN true ELSE false END FROM Game g WHERE g.coverImage.id = :imageId OR g.headerImage.id = :imageId OR :imageId IN (SELECT i.id FROM g.images i)")
    fun existsByImage(@Param("imageId") imageId: Long): Boolean

    @Query("SELECT DISTINCT p FROM Game g JOIN g.platforms p")
    fun findAllDistinctPlatforms(): Set<Platform>

    @Query("SELECT DISTINCT p FROM Game g JOIN g.platforms p WHERE g.library.id = :libraryId")
    fun findDistinctPlatformsByLibrary(@Param("libraryId") libraryId: Long): Set<Platform>

}