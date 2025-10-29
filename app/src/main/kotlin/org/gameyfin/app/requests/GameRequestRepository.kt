package org.gameyfin.app.requests

import org.gameyfin.app.requests.entities.GameRequest
import org.gameyfin.app.requests.status.GameRequestStatus
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface GameRequestRepository : JpaRepository<GameRequest, Long> {
    @Query("SELECT g FROM GameRequest g WHERE g.title = :title AND YEAR(g.release) = YEAR(:release) AND g.platform = :platform")
    fun findByTitleAndReleaseYearAndPlatform(
        @Param("title") title: String,
        @Param("release") release: Instant?,
        @Param("platform") platform: Platform
    ): List<GameRequest>

    @Query("SELECT g FROM GameRequest g WHERE g.title = :title AND YEAR(g.release) = YEAR(:release) AND g.platform = :platform AND g.status NOT IN (:excludedStatuses)")
    fun findRequestsByTitleAndReleaseYearAndPlatformAndStatusNotIn(
        @Param("title") title: String,
        @Param("release") release: Instant?,
        @Param("platform") platform: Platform,
        @Param("excludedStatuses") excludedStatuses: List<GameRequestStatus>
    ): List<GameRequest>

    @Query("SELECT g FROM GameRequest g WHERE g.requester.id = :requesterId AND g.status IN (:statuses)")
    fun findRequestsByRequesterIdAndStatusIn(
        @Param("requesterId") requesterId: Long?,
        @Param("statuses") statuses: List<GameRequestStatus>
    ): List<GameRequest>
}