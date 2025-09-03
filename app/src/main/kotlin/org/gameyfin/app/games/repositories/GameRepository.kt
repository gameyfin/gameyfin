package org.gameyfin.app.games.repositories

import org.gameyfin.app.games.entities.Game
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
}