package org.gameyfin.app.games.repositories

import org.gameyfin.app.games.entities.Game
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository

interface GameRepository : JpaRepository<Game, Long> {
    fun findByMetadata_Path(path: String): Game?
    fun findAllByMetadata_PathIn(paths: List<String>): List<Game>
    fun findByOrderByCreatedAtDesc(limit: Limit): List<Game>
    fun findByOrderByUpdatedAtDesc(limit: Limit): List<Game>
}