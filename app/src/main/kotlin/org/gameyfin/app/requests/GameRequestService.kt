package org.gameyfin.app.requests

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.core.events.GameCreatedEvent
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.core.security.isAdmin
import org.gameyfin.app.requests.dto.GameRequestCreationDto
import org.gameyfin.app.requests.dto.GameRequestDto
import org.gameyfin.app.requests.dto.GameRequestEvent
import org.gameyfin.app.requests.entities.GameRequest
import org.gameyfin.app.requests.extensions.toDto
import org.gameyfin.app.requests.extensions.toDtos
import org.gameyfin.app.requests.status.GameRequestStatus
import org.gameyfin.app.users.UserService
import org.gameyfin.app.users.entities.User
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

@Service
class GameRequestService(
    private val gameRequestRepository: GameRequestRepository,
    private val userService: UserService,
    private val config: ConfigService
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

        // Check if requests are enabled
        if (config.get(ConfigProperties.Requests.Games.Enabled) != true) {
            throw IllegalStateException("Game requests are disabled")
        }

        // Check if a request with the same title and release year already exists
        val existingRequests = gameRequestRepository.findOpenRequestsByTitleAndReleaseYear(
            gameRequest.title,
            gameRequest.release,
            emptyList()
        )
        if (existingRequests.isNotEmpty()) {
            throw IllegalStateException("A request for this game already exists (ID: ${existingRequests[0].id})")
        }

        val auth = getCurrentAuth()
        val currentUser = auth?.let { userService.getByUsername(it.name) }

        // Check if guests are allowed to create requests
        if (config.get(ConfigProperties.Requests.Games.AllowGuestsToRequestGames) != true && currentUser == null) {
            throw IllegalStateException("Only registered users can create game requests")
        }

        // Check if user has too many open requests (0 means no limit per user)
        // Note: All guests are treated as a single user with null ID and thus share their request limit
        // Note: Admins are exempt from this limit
        val openRequestsForUser = gameRequestRepository.findOpenRequestsByRequesterId(currentUser?.id)
        val maxRequestsPerUser = config.get(ConfigProperties.Requests.Games.MaxOpenRequestsPerUser) ?: 0
        if (maxRequestsPerUser == 0 || (auth?.isAdmin() != true && openRequestsForUser.size >= maxRequestsPerUser)) {
            throw IllegalStateException("You have reached the maximum number of open requests (${maxRequestsPerUser})")
        }

        val newGameRequest = GameRequest(
            title = gameRequest.title,
            release = gameRequest.release,
            status = GameRequestStatus.PENDING,
            requester = currentUser,
            voters = mutableSetOf<User>().apply {
                currentUser?.let { add(it) }
            }
        )

        gameRequestRepository.save(newGameRequest)
    }

    fun deleteRequest(id: Long) {
        val gameRequest = gameRequestRepository.findById(id)
            .orElseThrow { NoSuchElementException("No game request found with id $id") }

        val auth = getCurrentAuth()
        val currentUser = auth?.let { userService.getByUsername(it.name) }
        val requester = gameRequest.requester

        // Check if the current user is the requester or an admin
        if (auth?.isAdmin() != true || requester == null || requester.id != currentUser?.id) {
            throw IllegalStateException("Only the requester or an admin can delete a game request")
        }

        gameRequestRepository.delete(gameRequest)
    }

    fun changeRequestStatus(id: Long, status: GameRequestStatus) {
        val gameRequest = gameRequestRepository.findById(id)
            .orElseThrow { NoSuchElementException("No game request found with id $id") }

        if (gameRequest.status == GameRequestStatus.FULFILLED) {
            log.debug { "Status of requests with status ${GameRequestStatus.FULFILLED} can't be changed" }
            return
        }

        gameRequest.status = status
        gameRequestRepository.save(gameRequest)
    }

    @Transactional
    fun toggleRequestVote(id: Long) {
        val auth = getCurrentAuth() ?: throw IllegalStateException("No authentication found")
        val currentUser =
            userService.getByUsername(auth.name) ?: throw IllegalStateException("Current user not found")
        val gameRequest = gameRequestRepository.findById(id)
            .orElseThrow { NoSuchElementException("No game request found with id $id") }

        // Replace the voters collection to ensure Hibernate detects the change
        val updatedVoters = gameRequest.voters.toMutableSet()
        if (updatedVoters.contains(currentUser)) {
            updatedVoters.remove(currentUser)
        } else {
            updatedVoters.add(currentUser)
        }
        gameRequest.voters = updatedVoters

        // Ensure the entity is marked as dirty
        gameRequest.status = gameRequest.status

        gameRequestRepository.save(gameRequest)
    }

    @Async
    @EventListener(GameCreatedEvent::class)
    fun completeMatchingRequests(gameCreatedEvent: GameCreatedEvent) {
        val game = gameCreatedEvent.game
        val gameTitle = game.title
        val gameRelease = game.release

        if (gameTitle == null) {
            log.warn { "Game '${game.id}' is missing title, cannot complete matching requests" }
            return
        }

        val matchingRequests = gameRequestRepository.findOpenRequestsByTitleAndReleaseYear(
            gameTitle,
            gameRelease
        )

        matchingRequests.forEach { request ->
            request.status = GameRequestStatus.FULFILLED
            request.linkedGameId = game.id
            val persistedRequest = gameRequestRepository.save(request)
            emit(GameRequestEvent.Updated(persistedRequest.toDto()))
            log.info { "Marked game request '${request.title}' (${request.release}) as FULFILLED because game is now available" }
        }
    }
}