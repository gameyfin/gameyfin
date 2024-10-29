package de.grimsi.gameyfin.games

import org.springframework.data.jpa.repository.JpaRepository

interface GameRepository : JpaRepository<Game, Long> {
    fun findByPath(path: String): Game?
}