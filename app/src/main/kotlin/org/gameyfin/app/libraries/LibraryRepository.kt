package org.gameyfin.app.libraries

import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface LibraryRepository : JpaRepository<Library, Long> {
    @Query("SELECT d.internalPath, l FROM Library l JOIN l.directories d WHERE d.internalPath IN :paths")
    fun findByPaths(paths: List<String>): List<Pair<String, Library>>

    @Query("SELECT DISTINCT p FROM Library l JOIN l.platforms p")
    fun findAllDistinctPlatforms(): Set<Platform>

    /* Synchronization */

    fun findAllByUpdatedAtAfter(since: Instant): List<Library>

    @Query("SELECT l.id FROM Library l")
    fun findAllIds(): List<Long>

    @Query("SELECT DISTINCT l.id FROM Library l JOIN l.directories d WHERE d.id IN :directoryMappingIds")
    fun findIdsByDirectoryMappingIds(@Param("directoryMappingIds") directoryMappingIds: Collection<Long>): List<Long>

    /**
     * Bulk update of the timestamp, e.g. if only an associated collection or child entity has changed.
     * Bypasses entity listeners.
     */
    @Modifying
    @Query("UPDATE Library l SET l.updatedAt = :updatedAt WHERE l.id IN :ids")
    fun bumpUpdatedAt(@Param("ids") ids: Collection<Long>, @Param("updatedAt") updatedAt: Instant): Int
}