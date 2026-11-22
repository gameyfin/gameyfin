package org.gameyfin.app.social

import org.gameyfin.app.config.ConfigProperties.Social
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.users.persistence.UserRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
class GameSocialService(
    private val jdbc: JdbcTemplate,
    private val games: GameRepository,
    private val users: UserRepository,
    private val members: MemberIdentity,
    private val config: ConfigService
) {
    fun settings() = SocialSettings(reviewsEnabled(), config.get(Social.AllowUserCollections) == true)

    @Transactional(readOnly = true)
    fun get(gameId: Long): GameSocialDto {
        checkGame(gameId)
        val memberId = members.id()
        val enabled = reviewsEnabled()
        val rating = jdbc.queryForObject(
            "SELECT AVG(CAST(rating AS DOUBLE PRECISION)), COUNT(rating) FROM member_game_state WHERE game_id = ?",
            { rs, _ -> rs.getObject(1, Double::class.javaObjectType) to rs.getLong(2) }, gameId
        )
        val mine = memberId?.let { state(gameId, it) }
        val published = if (enabled) publishedReviews(gameId) else emptyList()
        val own = if (enabled && memberId != null) jdbc.query(
            """SELECT published_text, submitted_text, rejected FROM game_review WHERE game_id = ? AND member_id = ?""",
            { rs, _ -> OwnReview(rs.getString(1), rs.getString(2), rs.getBoolean(3)) }, gameId, memberId
        ).singleOrNull() else null
        return GameSocialDto(rating.first, rating.second, mine?.first, mine?.second, enabled, published, own)
    }

    @Transactional
    fun rate(gameId: Long, rating: Int?) {
        require(rating == null || rating in 1..5) { "Rating must be between 1 and 5" }
        val memberId = lockMember(gameId)
        saveState(gameId, memberId, rating, state(gameId, memberId)?.second)
    }

    @Transactional
    fun setPlayStatus(gameId: Long, status: PlayStatus?): Boolean {
        val memberId = lockMember(gameId)
        val previous = state(gameId, memberId)
        saveState(gameId, memberId, previous?.first, status)
        return status == PlayStatus.PLAYED && previous?.second != PlayStatus.PLAYED && reviewsEnabled() &&
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM game_review WHERE game_id = ? AND member_id = ?",
                Long::class.javaObjectType, gameId, memberId
            ) == 0L
    }

    @Transactional
    fun saveReview(gameId: Long, text: String) {
        requireReviewsEnabled()
        require(text.isNotBlank() && text.length <= 10_000) { "Review must have 1–10,000 characters" }
        val memberId = lockMember(gameId)
        // The member row serializes first submissions. Moderation takes the same lock and matches the stored version.
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val version = UUID.randomUUID().toString()
        val approval = config.get(Social.RequireReviewApproval) == true
        val changed = if (approval) {
            jdbc.update(
                """UPDATE game_review SET submitted_text = ?, submitted_at = ?, submitted_version = ?, rejected = FALSE
                   WHERE game_id = ? AND member_id = ?""", text, now, version, gameId, memberId
            )
        } else {
            jdbc.update(
                """UPDATE game_review SET published_text = ?, published_at = ?, published_version = ?,
                   submitted_text = NULL, submitted_at = NULL, submitted_version = NULL, rejected = FALSE
                   WHERE game_id = ? AND member_id = ?""", text, now, version, gameId, memberId
            )
        }
        if (changed == 0) {
            // The choice of columns is fixed by the approval setting.
            val prefix = if (approval) "submitted" else "published"
            jdbc.update(
                "INSERT INTO game_review (game_id, member_id, ${prefix}_text, ${prefix}_at, ${prefix}_version) VALUES (?, ?, ?, ?, ?)",
                gameId, memberId, text, now, version
            )
        }
    }

    @Transactional
    fun deleteReview(gameId: Long) {
        requireReviewsEnabled()
        val memberId = lockMember(gameId)
        jdbc.update("DELETE FROM game_review WHERE game_id = ? AND member_id = ?", gameId, memberId)
    }

    @Transactional(readOnly = true)
    fun moderationQueue(): List<ReviewModerationEntry> {
        if (!reviewsEnabled()) return emptyList()
        val entries = jdbc.query(
            """SELECT r.game_id, g.title, member_id, submitted_text AS text, submitted_at AS review_date,
                      submitted_version AS version, TRUE AS pending
               FROM game_review r JOIN game g ON g.id = r.game_id
               WHERE submitted_version IS NOT NULL AND NOT rejected
               UNION ALL
               SELECT r.game_id, g.title, member_id, published_text, published_at, published_version, FALSE
               FROM game_review r JOIN game g ON g.id = r.game_id WHERE published_version IS NOT NULL
               ORDER BY pending DESC, review_date DESC, game_id DESC, member_id DESC""",
            { rs, _ -> ReviewModerationEntry(
                rs.getLong("game_id"), rs.getString("title"), rs.getLong("member_id"), "", rs.getString("text"),
                rs.instant("review_date"), rs.getString("version"), rs.getBoolean("pending")
            ) }
        )
        val authors = authorNames(entries.map { it.memberId })
        return entries.mapNotNull { entry -> authors[entry.memberId]?.let { entry.copy(author = it) } }
    }

    @Transactional
    fun moderate(gameId: Long, memberId: Long, version: String, approve: Boolean): Boolean {
        requireReviewsEnabled()
        if (users.findForUpdate(memberId) == null) return false
        val change = if (approve) {
            """published_text = submitted_text, published_at = CURRENT_TIMESTAMP, published_version = submitted_version,
               submitted_text = NULL, submitted_at = NULL, submitted_version = NULL, rejected = FALSE"""
        } else "rejected = TRUE"
        return jdbc.update(
            """UPDATE game_review SET $change WHERE game_id = ? AND member_id = ?
               AND submitted_version = ? AND NOT rejected""", gameId, memberId, version
        ) == 1
    }

    @Transactional
    fun removePublished(gameId: Long, memberId: Long, version: String): Boolean {
        requireReviewsEnabled()
        if (users.findForUpdate(memberId) == null) return false
        val removed = jdbc.update(
            """UPDATE game_review SET published_text = NULL, published_at = NULL, published_version = NULL
               WHERE game_id = ? AND member_id = ? AND published_version = ?""", gameId, memberId, version
        ) == 1
        if (removed) jdbc.update(
            """DELETE FROM game_review WHERE game_id = ? AND member_id = ?
               AND published_version IS NULL AND submitted_version IS NULL""", gameId, memberId
        )
        return removed
    }

    private fun publishedReviews(gameId: Long): List<PublishedReview> {
        val rows = jdbc.query(
            """SELECT member_id, published_text, published_at FROM game_review
               WHERE game_id = ? AND published_version IS NOT NULL ORDER BY published_at DESC, member_id DESC""",
            { rs, _ -> Triple(rs.getLong(1), rs.getString(2), rs.instant("published_at")) }, gameId
        )
        val authors = authorNames(rows.map { it.first })
        return rows.mapNotNull { (id, text, date) -> authors[id]?.let { PublishedReview(it, text, date) } }
    }

    private fun authorNames(ids: List<Long>) = users.findAllById(ids.distinct()).associate { it.id!! to it.username }

    private fun state(gameId: Long, memberId: Long) = jdbc.query(
        "SELECT rating, play_status FROM member_game_state WHERE game_id = ? AND member_id = ?",
        RowMapper { rs, _ ->
            rs.getObject(1, Int::class.javaObjectType) to rs.getString(2)?.let(PlayStatus::valueOf)
        }, gameId, memberId
    ).singleOrNull()

    private fun saveState(gameId: Long, memberId: Long, rating: Int?, status: PlayStatus?) {
        if (rating == null && status == null) {
            jdbc.update("DELETE FROM member_game_state WHERE game_id = ? AND member_id = ?", gameId, memberId)
        } else {
            val changed = jdbc.update(
                "UPDATE member_game_state SET rating = ?, play_status = ? WHERE game_id = ? AND member_id = ?",
                rating, status?.name, gameId, memberId
            )
            if (changed == 0) jdbc.update(
                "INSERT INTO member_game_state (game_id, member_id, rating, play_status) VALUES (?, ?, ?, ?)",
                gameId, memberId, rating, status?.name
            )
        }
    }

    private fun lockMember(gameId: Long): Long {
        val memberId = members.requireId()
        members.lock(memberId)
        checkGame(gameId)
        return memberId
    }

    private fun checkGame(gameId: Long) {
        if (!games.existsById(gameId)) throw NoSuchElementException("Game not found")
    }

    private fun reviewsEnabled() = config.get(Social.EnableReviews) == true

    private fun requireReviewsEnabled() {
        if (!reviewsEnabled()) throw AccessDeniedException("Reviews are disabled")
    }

    private fun ResultSet.instant(column: String) = getObject(column, OffsetDateTime::class.java).toInstant()
}
