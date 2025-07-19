package org.gameyfin.app.libraries

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface LibraryRepository : JpaRepository<Library, Long> {
    @Query("SELECT d.internalPath, l FROM Library l JOIN l.directories d WHERE d.internalPath IN :paths")
    fun findByPaths(paths: List<String>): List<Pair<String, Library>>
}