package org.gameyfin.app.requests

import org.gameyfin.app.requests.entities.GameRequest
import org.gameyfin.app.requests.status.GameRequestStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface GameRequestRepository : JpaRepository<GameRequest, Long> {
    @Query("SELECT g FROM GameRequest g WHERE g.title = :title AND YEAR(g.release) = YEAR(:release) AND g.status NOT IN (:excludedStatuses)")
    fun findOpenRequestsByTitleAndReleaseYear(
        @Param("title") title: String,
        @Param("release") release: Instant?,
        @Param("excludedStatuses") excludedStatuses: List<GameRequestStatus> = listOf(
            GameRequestStatus.FULFILLED,
            GameRequestStatus.REJECTED
        )
    ): List<GameRequest>
}