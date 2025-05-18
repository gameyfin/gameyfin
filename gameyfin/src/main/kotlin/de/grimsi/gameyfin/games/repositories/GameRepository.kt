package de.grimsi.gameyfin.games.repositories

import de.grimsi.gameyfin.games.entities.Game
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository

interface GameRepository : JpaRepository<Game, Long> {
    fun findByPath(path: String): Game?
    fun findAllByPathIn(paths: List<String>): List<Game>
    fun findByOrderByCreatedAtDesc(limit: Limit): List<Game>
    fun findByOrderByUpdatedAtDesc(limit: Limit): List<Game>
}