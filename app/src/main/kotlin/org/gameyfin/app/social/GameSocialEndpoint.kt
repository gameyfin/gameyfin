package org.gameyfin.app.social

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.annotations.DynamicPublicAccess
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers

@Endpoint
@AnonymousAllowed
@DynamicPublicAccess
class GameSocialEndpoint(private val social: GameSocialService) {
    fun get(gameId: Long) = social.get(gameId)

    fun memberRatings() = social.memberRatings()

    fun subscribeSettings(): Flux<SocialSettings> = Flux.merge(
        ConfigService.subscribe()
            .filter { updates -> updates.any { it.updates.keys.any { key -> key.startsWith("social.") } } }
            .map { },
        Flux.just(Unit)
    ).publishOn(Schedulers.boundedElastic()).map { social.settings() }.distinctUntilChanged()

    @PermitAll
    fun myPlayStatuses() = social.myPlayStatuses()

    @PermitAll
    fun rate(gameId: Long, rating: Int?) = social.rate(gameId, rating)

    @PermitAll
    fun setPlayStatus(gameId: Long, status: PlayStatus?) = social.setPlayStatus(gameId, status)

    @PermitAll
    fun saveReview(gameId: Long, text: String) = social.saveReview(gameId, text)

    @PermitAll
    fun deleteReview(gameId: Long) = social.deleteReview(gameId)

    @RolesAllowed(Role.Names.ADMIN)
    fun moderationQueue() = social.moderationQueue()

    @RolesAllowed(Role.Names.ADMIN)
    fun moderate(gameId: Long, memberId: Long, version: String, approve: Boolean) =
        social.moderate(gameId, memberId, version, approve)

    @RolesAllowed(Role.Names.ADMIN)
    fun removePublished(gameId: Long, memberId: Long, version: String) =
        social.removePublished(gameId, memberId, version)
}
