package org.gameyfin.app.games.repositories

import org.gameyfin.app.games.entities.Game
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface GameRepository : JpaRepository<Game, Long> {
    @Query("""
        SELECT g FROM Game g JOIN g.platforms p
        WHERE g.title = :title
        AND :releaseYear IS NOT NULL
        AND EXTRACT(YEAR FROM g.release) = :releaseYear
        AND p = :platform
    """)
    fun findByTitleAndReleaseYearAndPlatform(
        @Param("title") title: String,
        @Param("releaseYear") releaseYear: Int?,
        @Param("platform") platform: Platform
    ): List<Game>

    @Query("SELECT CASE WHEN COUNT(g) > 0 THEN true ELSE false END FROM Game g WHERE g.coverImage.id = :imageId OR g.headerImage.id = :imageId OR :imageId IN (SELECT i.id FROM g.images i)")
    fun existsByImage(@Param("imageId") imageId: Long): Boolean

    @Query("SELECT DISTINCT g FROM Game g LEFT JOIN g.images i WHERE g.coverImage.id = :imageId OR g.headerImage.id = :imageId OR i.id = :imageId")
    fun findAllByImage(@Param("imageId") imageId: Long): List<Game>

    @Query("SELECT DISTINCT p FROM Game g JOIN g.platforms p")
    fun findAllDistinctPlatforms(): Set<Platform>

    /* Synchronization */

    fun findAllByUpdatedAtAfter(since: Instant): List<Game>

    @Query("SELECT g.id FROM Game g")
    fun findAllIds(): List<Long>

    @Query("SELECT DISTINCT g.id FROM Game g JOIN g.metadata.fields f WHERE f.id IN :fieldMetadataIds")
    fun findIdsByFieldMetadataIds(@Param("fieldMetadataIds") fieldMetadataIds: Collection<Long>): List<Long>

    @Query("SELECT g.id FROM Game g WHERE g.coverImage.id IN :imageIds")
    fun findIdsByCoverImageIds(@Param("imageIds") imageIds: Collection<Long>): List<Long>

    @Query("SELECT g.id FROM Game g WHERE g.headerImage.id IN :imageIds")
    fun findIdsByHeaderImageIds(@Param("imageIds") imageIds: Collection<Long>): List<Long>

    @Query("SELECT DISTINCT g.id FROM Game g JOIN g.images i WHERE i.id IN :imageIds")
    fun findIdsByGalleryImageIds(@Param("imageIds") imageIds: Collection<Long>): List<Long>

    /**
     * Bulk update of the timestamp, e.g. if only an associated collection or child entity has changed.
     * Bypasses entity listeners.
     */
    @Modifying
    @Query("UPDATE Game g SET g.updatedAt = :updatedAt WHERE g.id IN :ids")
    fun bumpUpdatedAt(@Param("ids") ids: Collection<Long>, @Param("updatedAt") updatedAt: Instant): Int
}