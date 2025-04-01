package de.grimsi.gameyfin.libraries

import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface LibraryRepository : JpaRepository<Library, Long> {
    @EntityGraph(attributePaths = ["games"])
    @Query("SELECT l FROM Library l ORDER BY function('RAND') LIMIT 1")
    fun findRandomLibrary(): Library?
}