package org.gameyfin.app.requests

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.requests.dto.GameRequestCreationDto
import org.gameyfin.app.requests.dto.GameRequestDto
import org.gameyfin.app.requests.dto.GameRequestEvent
import org.gameyfin.app.requests.entities.GameRequest
import org.gameyfin.app.requests.extensions.toDto
import org.gameyfin.app.requests.extensions.toDtos
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
        private val gameRequestEvents = Sinks.many().multicast().onBackpressureBuffer<GameRequestEvent>(1024, false)

        fun subscribe(): Flux<List<GameRequestEvent>> {
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

        fun emit(event: GameRequestEvent) {
            gameRequestEvents.tryEmitNext(event)
        }
    }

    fun getAll(): List<GameRequestDto> {
        val entities = gameRequestRepository.findAll()
        return entities.toDtos()
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

    fun deleteRequest(id: Long) {
        val gameRequest = gameRequestRepository.findById(id)
            .orElseThrow { NoSuchElementException("No game request found with id $id") }
        gameRequestRepository.delete(gameRequest)
    }

    fun changeRequestStatus(id: Long, status: GameRequestStatus) {
        val gameRequest = gameRequestRepository.findById(id)
            .orElseThrow { NoSuchElementException("No game request found with id $id") }
        gameRequest.status = status
        gameRequestRepository.save(gameRequest)
    }

    fun toggleRequestVote(id: Long) {
        val currentUser =
            userService.getByUsername(getCurrentAuth().name) ?: throw IllegalStateException("Current user not found")
        val gameRequest = gameRequestRepository.findById(id)
            .orElseThrow { NoSuchElementException("No game request found with id $id") }

        if (gameRequest.requester?.id == currentUser.id) {
            throw IllegalStateException("You cannot vote for your own request")
        }

        if (gameRequest.voters.contains(currentUser)) {
            gameRequest.voters.remove(currentUser)
        } else {
            gameRequest.voters.add(currentUser)
        }

        gameRequestRepository.save(gameRequest)
    }

    fun completeMatchingRequests(game: Game) {
        val gameTitle = game.title
        val gameRelease = game.release

        if (gameTitle == null || gameRelease == null) {
            log.debug { "Game '${game.id}' is missing title and/or release date, cannot complete matching requests" }
            return
        }

        // First match by exact title and release date, if not result could be found then by title and release year only
        val matchingRequestsByExactRelease = gameRequestRepository.findByTitleAndRelease(gameTitle, gameRelease)
        val matchingRequestsByReleaseYear = matchingRequestsByExactRelease.ifEmpty {
            gameRequestRepository.findByTitleAndReleaseYear(
                gameTitle,
                gameRelease
            )
        }

        matchingRequestsByReleaseYear.forEach { request ->
            request.status = GameRequestStatus.FULFILLED
            request.linkedGameId = game.id
            val persistedRequest = gameRequestRepository.save(request)
            emit(GameRequestEvent.Updated(persistedRequest.toDto()))
            log.info { "Marked game request '${request.title}' (${request.release}) as FULFILLED because game is now available" }
        }
    }
}