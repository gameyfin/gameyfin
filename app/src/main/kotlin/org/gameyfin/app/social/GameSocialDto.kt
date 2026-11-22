package org.gameyfin.app.social

import java.time.Instant

enum class PlayStatus { WANT_TO_PLAY, PLAYING, PLAYED }

data class SocialSettings(val reviewsEnabled: Boolean, val allowUserCollections: Boolean)

data class GameSocialDto(
    val averageRating: Double?,
    val ratingCount: Long,
    val myRating: Int?,
    val myPlayStatus: PlayStatus?,
    val reviewsEnabled: Boolean,
    val reviews: List<PublishedReview>,
    val myReview: OwnReview?
)

data class PublishedReview(val author: String, val text: String, val publishedAt: Instant)

data class OwnReview(val publishedText: String?, val submittedText: String?, val rejected: Boolean)

data class ReviewModerationEntry(
    val gameId: Long,
    val gameTitle: String,
    val memberId: Long,
    val author: String,
    val text: String,
    val date: Instant,
    val version: String,
    val pending: Boolean
)
