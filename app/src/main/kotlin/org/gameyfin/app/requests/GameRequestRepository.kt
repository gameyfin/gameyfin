package org.gameyfin.app.requests

import org.gameyfin.app.requests.entities.GameRequest
import org.springframework.data.jpa.repository.JpaRepository

interface GameRequestRepository : JpaRepository<GameRequest, Long>