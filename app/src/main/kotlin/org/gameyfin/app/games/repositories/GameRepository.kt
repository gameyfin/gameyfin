package org.gameyfin.app.games.repositories

import org.gameyfin.app.games.entities.Game
import org.springframework.data.jpa.repository.JpaRepository

interface GameRepository : JpaRepository<Game, Long>