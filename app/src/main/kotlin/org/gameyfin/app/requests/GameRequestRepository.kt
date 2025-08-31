package org.gameyfin.app.requests

import org.springframework.data.jpa.repository.JpaRepository

interface GameRequestRepository : JpaRepository<GameRequest, Long>