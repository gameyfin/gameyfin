package org.gameyfin.app.requests

import org.gameyfin.app.requests.entities.GameRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface GameRequestRepository : JpaRepository<GameRequest, Long> {
    fun findByTitleAndRelease(title: String, release: Instant): List<GameRequest>

    @Query("SELECT g FROM GameRequest g WHERE g.title = :title AND YEAR(g.release) = YEAR(:release)")
    fun findByTitleAndReleaseYear(@Param("title") title: String, @Param("release") release: Instant): List<GameRequest>
}