package org.gameyfin.app.social.recommendations

import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.PermitAll

@Endpoint
@PermitAll
class RecommendationEndpoint(private val recommendations: RecommendationService) {
    fun discover(): DiscoveryResult = recommendations.discover()

    fun sharesActivity(): Boolean = recommendations.sharesActivity()

    fun setSharesActivity(enabled: Boolean): Boolean = recommendations.setSharesActivity(enabled)
}

data class DiscoveryResult(val kind: DiscoveryKind, val recommendations: List<GameRecommendation>)

enum class DiscoveryKind { PERSONALIZED, POPULAR, EMPTY }

data class GameRecommendation(
    val gameId: Long,
    val matchPercentage: Int,
    val similarToGameId: Long? = null,
    val sharedTag: String? = null
)
