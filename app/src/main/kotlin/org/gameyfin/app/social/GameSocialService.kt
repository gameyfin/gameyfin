package org.gameyfin.app.social

import org.gameyfin.app.config.ConfigProperties.Social
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.users.persistence.UserRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.*

@Service
class GameSocialService(
    private val states: MemberGameStateRepository,
    private val reviews: GameReviewRepository,
    private val games: GameRepository,
    private val users: UserRepository,
    private val members: MemberIdentity,
    private val config: ConfigService
) {
    fun settings() = SocialSettings(
        reviewsEnabled(),
        config.get(Social.AllowUserCollections) == true,
        config.get(Social.EnableRecommendations) == true
    )

    @Transactional(readOnly = true)
    fun get(gameId: Long): GameSocialDto {
        checkGame(gameId)
        val memberId = members.id()
        val enabled = reviewsEnabled()
        val rating = states.ratings(gameId)
        val key = memberId?.let { MemberGameId(gameId, it) }
        val mine = key?.let(states::findByIdOrNull)
        val published = if (enabled) reviews.published(gameId) else emptyList()
        val own = if (enabled && key != null) reviews.findByIdOrNull(key)?.let {
            OwnReview(it.publishedText, it.submittedText, it.rejected)
        } else null
        return GameSocialDto(rating.average, rating.count, mine?.rating, mine?.playStatus, enabled, published, own)
    }

    @Transactional(readOnly = true)
    fun memberRatings(): List<GameMemberRating> = states.memberRatings()

    @Transactional(readOnly = true)
    fun myPlayStatuses(): List<GamePlayStatus> = states.playStatuses(members.requireId())

    @Transactional
    fun rate(gameId: Long, rating: Int?) {
        require(rating == null || rating in 1..5) { "Rating must be between 1 and 5" }
        val state = lockedState(gameId)
        state.rating = rating
        saveState(state)
    }

    @Transactional
    fun setPlayStatus(gameId: Long, status: PlayStatus?): Boolean {
        val state = lockedState(gameId)
        val previous = state.playStatus
        state.playStatus = status
        saveState(state)
        return status == PlayStatus.PLAYED && previous != status && reviewsEnabled() && !reviews.existsById(state.id)
    }

    @Transactional
    fun saveReview(gameId: Long, text: String) {
        requireReviewsEnabled()
        require(text.isNotBlank() && text.length <= 10_000) { "Review must have 1–10,000 characters" }
        val memberId = lockMember(gameId)
        // The member row serializes first submissions. Moderation takes the same lock and matches the stored version.
        val now = Instant.now()
        val version = UUID.randomUUID().toString()
        val key = MemberGameId(gameId, memberId)
        val review = reviews.findByIdOrNull(key) ?: GameReview(key)
        if (config.get(Social.RequireReviewApproval) == true) {
            review.submittedText = text
            review.submittedAt = now
            review.submittedVersion = version
            review.rejected = false
        } else {
            review.publish(text, now, version)
        }
        reviews.save(review)
    }

    @Transactional
    fun deleteReview(gameId: Long) {
        requireReviewsEnabled()
        val memberId = lockMember(gameId)
        reviews.deleteById(MemberGameId(gameId, memberId))
    }

    @Transactional(readOnly = true)
    fun moderationQueue(): List<ReviewModerationEntry> {
        if (!reviewsEnabled()) return emptyList()
        return reviews.moderationQueue()
    }

    @Transactional
    fun moderate(gameId: Long, memberId: Long, version: String, approve: Boolean): Boolean {
        requireReviewsEnabled()
        if (users.findForUpdate(memberId) == null) return false
        val review = reviews.findByIdOrNull(MemberGameId(gameId, memberId)) ?: return false
        if (review.submittedVersion != version || review.rejected) return false
        if (approve) review.publish(requireNotNull(review.submittedText), Instant.now(), version)
        else review.rejected = true
        return true
    }

    @Transactional
    fun removePublished(gameId: Long, memberId: Long, version: String): Boolean {
        requireReviewsEnabled()
        if (users.findForUpdate(memberId) == null) return false
        val review = reviews.findByIdOrNull(MemberGameId(gameId, memberId)) ?: return false
        if (review.publishedVersion != version) return false
        review.publishedText = null
        review.publishedAt = null
        review.publishedVersion = null
        if (review.submittedVersion == null) reviews.delete(review)
        return true
    }

    private fun lockedState(gameId: Long): MemberGameState {
        val key = MemberGameId(gameId, lockMember(gameId))
        return states.findByIdOrNull(key) ?: MemberGameState(key)
    }

    private fun saveState(state: MemberGameState) {
        if (state.rating == null && state.playStatus == null) states.deleteById(state.id)
        else states.save(state)
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
}
