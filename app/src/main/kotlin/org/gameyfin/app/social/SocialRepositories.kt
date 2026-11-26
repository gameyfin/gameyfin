package org.gameyfin.app.social

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.io.Serializable
import java.time.Instant

@Embeddable
data class MemberGameId(var gameId: Long, var memberId: Long) : Serializable

@Entity
class MemberGameState(
    @EmbeddedId var id: MemberGameId,
    var rating: Int? = null,
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) var playStatus: PlayStatus? = null
)

data class RatingSummary(val average: Double?, val count: Long)

data class MemberActivity(val memberId: Long, val gameId: Long, val rating: Int?, val status: PlayStatus?)

interface MemberGameStateRepository : JpaRepository<MemberGameState, MemberGameId> {
    @Query("select new org.gameyfin.app.social.RatingSummary(avg(s.rating), count(s.rating)) from MemberGameState s where s.id.gameId = :gameId")
    fun ratings(gameId: Long): RatingSummary

    @Query("""select new org.gameyfin.app.social.GameMemberRating(s.id.gameId, avg(s.rating))
        from MemberGameState s where s.rating is not null group by s.id.gameId""")
    fun memberRatings(): List<GameMemberRating>

    @Query("""select new org.gameyfin.app.social.GamePlayStatus(s.id.gameId, s.playStatus)
        from MemberGameState s where s.id.memberId = :memberId and s.playStatus is not null""")
    fun playStatuses(memberId: Long): List<GamePlayStatus>

    @Query("""select new org.gameyfin.app.social.MemberActivity(s.id.memberId, s.id.gameId, s.rating, s.playStatus)
        from MemberGameState s where s.id.memberId in :memberIds""")
    fun activity(memberIds: List<Long>): List<MemberActivity>
}

@Entity
class GameReview(
    @EmbeddedId var id: MemberGameId,
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) var publishedText: String? = null,
    var publishedAt: Instant? = null,
    var publishedVersion: String? = null,
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) var submittedText: String? = null,
    var submittedAt: Instant? = null,
    var submittedVersion: String? = null,
    var rejected: Boolean = false
) {
    fun publish(text: String, at: Instant, version: String) {
        publishedText = text
        publishedAt = at
        publishedVersion = version
        submittedText = null
        submittedAt = null
        submittedVersion = null
        rejected = false
    }
}

interface GameReviewRepository : JpaRepository<GameReview, MemberGameId> {
    @Query("""select new org.gameyfin.app.social.PublishedReview(u.username, r.publishedText, r.publishedAt)
        from GameReview r join User u on u.id = r.id.memberId
        where r.id.gameId = :gameId and r.publishedVersion is not null
        order by r.publishedAt desc, r.id.memberId desc""")
    fun published(gameId: Long): List<PublishedReview>

    @Query("""select new org.gameyfin.app.social.ReviewModerationEntry(
        q.gameId, q.gameTitle, q.memberId, q.author, q.text, q.date, q.version, q.pending)
        from (select r.id.gameId as gameId, g.title as gameTitle, u.id as memberId, u.username as author,
        r.submittedText as text, r.submittedAt as date, r.submittedVersion as version, true as pending
        from GameReview r join Game g on g.id = r.id.gameId join User u on u.id = r.id.memberId
        where r.submittedVersion is not null and r.rejected = false
        union all
        select r.id.gameId, g.title, u.id, u.username, r.publishedText, r.publishedAt, r.publishedVersion, false
        from GameReview r join Game g on g.id = r.id.gameId join User u on u.id = r.id.memberId
        where r.publishedVersion is not null) q
        order by q.pending desc, q.date desc, q.gameId desc, q.memberId desc""")
    fun moderationQueue(): List<ReviewModerationEntry>
}
