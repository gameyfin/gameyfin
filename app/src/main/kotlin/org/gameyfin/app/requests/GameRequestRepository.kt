package org.gameyfin.app.requests

import org.gameyfin.app.requests.entities.GameRequest
import org.gameyfin.app.requests.status.GameRequestStatus
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface GameRequestRepository : JpaRepository<GameRequest, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM GameRequest r WHERE r.id = :id")
    fun findForVote(@Param("id") id: Long): GameRequest?

    @Query(
        """
        SELECT g FROM GameRequest g
        WHERE g.title = :title
        AND :releaseYear IS NOT NULL
        AND EXTRACT(YEAR FROM g.release) = :releaseYear
        AND g.platform = :platform
    """
    )
    fun findByTitleAndReleaseYearAndPlatform(
        @Param("title") title: String,
        @Param("releaseYear") releaseYear: Int?,
        @Param("platform") platform: Platform
    ): List<GameRequest>

    @Query(
        """
        SELECT g FROM GameRequest g
        WHERE g.title = :title
        AND :releaseYear IS NOT NULL
        AND EXTRACT(YEAR FROM g.release) = :releaseYear
        AND g.platform = :platform
        AND g.status NOT IN (:excludedStatuses)
    """
    )
    fun findRequestsByTitleAndReleaseYearAndPlatformAndStatusNotIn(
        @Param("title") title: String,
        @Param("releaseYear") releaseYear: Int?,
        @Param("platform") platform: Platform,
        @Param("excludedStatuses") excludedStatuses: List<GameRequestStatus>
    ): List<GameRequest>

    @Query("SELECT g FROM GameRequest g WHERE g.requester.id = :requesterId AND g.status IN (:statuses)")
    fun findRequestsByRequesterIdAndStatusIn(
        @Param("requesterId") requesterId: Long?,
        @Param("statuses") statuses: List<GameRequestStatus>
    ): List<GameRequest>
}
