package org.gameyfin.app.requests

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.games.dto.GameUserEvent
import org.gameyfin.app.requests.dto.GameRequestCreationDto
import org.gameyfin.app.requests.status.GameRequestStatus
import org.gameyfin.app.users.UserService
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

@Service
class GameRequestService(
    private val gameRequestRepository: GameRequestRepository,
    private val userService: UserService
) {

    companion object {
        private val log = KotlinLogging.logger {}

        /* Websockets */
        private val gameRequestEvents = Sinks.many().multicast().onBackpressureBuffer<GameUserEvent>(1024, false)

        fun subscribe(): Flux<List<GameUserEvent>> {
            log.debug { "New user subscription for gameRequestEvents" }
            return gameRequestEvents.asFlux()
                .buffer(100.milliseconds.toJavaDuration())
                .doOnSubscribe {
                    log.debug { "Subscriber added to gameRequestEvents [${gameRequestEvents.currentSubscriberCount()}]" }
                }
                .doFinally {
                    log.debug { "Subscriber removed from gameRequestEvents with signal type $it [${gameRequestEvents.currentSubscriberCount()}]" }
                }
        }

        fun emit(event: GameUserEvent) {
            gameRequestEvents.tryEmitNext(event)
        }
    }

    fun createRequest(gameRequest: GameRequestCreationDto) {
        val currentUser = userService.getByUsername(getCurrentAuth().name)

        val gameRequest = GameRequest(
            title = gameRequest.title,
            release = gameRequest.release,
            status = GameRequestStatus.PENDING,
            externalProviderIds = gameRequest.externalProviderIds,
            requester = currentUser
        )

        gameRequestRepository.save(gameRequest)
    }
}