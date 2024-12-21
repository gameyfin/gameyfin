package de.grimsi.gameyfin.games.repositories

import de.grimsi.gameyfin.games.entities.Game
import org.springframework.data.jpa.repository.JpaRepository

interface GameRepository : JpaRepository<Game, Long> {
    fun findByPath(path: String): Game?
}