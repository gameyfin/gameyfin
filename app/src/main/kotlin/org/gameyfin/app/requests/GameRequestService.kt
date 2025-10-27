package org.gameyfin.app.requests

import com.vaadin.hilla.exception.EndpointException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.core.events.GameCreatedEvent
import org.gameyfin.app.core.events.GameUpdatedEvent
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.core.security.isAdmin
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.repositories.GameRepository
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
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.time.ZoneId
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

@Service
class GameRequestService(
    private val config: ConfigService,
    private val userService: UserService,
    private val gameRequestRepository: GameRequestRepository,
    private val gameRepository: GameRepository
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

    @Async
    @EventListener(GameCreatedEvent::class)
    fun onGameCreated(gameCreatedEvent: GameCreatedEvent) {
        completeMatchingRequests(gameCreatedEvent.game)
    }

    @Async
    @TransactionalEventListener(
        classes = [GameUpdatedEvent::class],
        phase = TransactionPhase.AFTER_COMPLETION
    )
    fun onGameUpdated(gameUpdatedEvent: GameUpdatedEvent) {
        completeMatchingRequests(gameUpdatedEvent.currentState)
    }

    fun getAll(): List<GameRequestDto> {
        val entities = gameRequestRepository.findAll()
        return entities.toDtos()
    }

    fun createRequest(gameRequest: GameRequestCreationDto) {

        // Check if requests are enabled
        if (config.get(ConfigProperties.Requests.Games.Enabled) != true) {
            throw EndpointException("Game requests are disabled")
        }

        // Check if game is already available
        val existingGames = gameRepository.findByTitleAndReleaseYearAndPlatform(
            gameRequest.title,
            gameRequest.release,
            gameRequest.platform
        )
        if (existingGames.isNotEmpty()) {
            throw EndpointException(
                "This game is already available for ${gameRequest.platform} (ID: ${existingGames[0].id})"
            )
        }

        // Check if a request with the same title, release year, and platform already exists
        val existingRequests = gameRequestRepository.findByTitleAndReleaseYearAndPlatform(
            gameRequest.title,
            gameRequest.release,
            gameRequest.platform
        )
        if (existingRequests.isNotEmpty()) {
            throw EndpointException("A request for this game on ${gameRequest.platform} already exists (ID: ${existingRequests[0].id})")
        }

        val auth = getCurrentAuth()
        val currentUser = auth?.let { userService.getByUsername(it.name) }

        // Check if guests are allowed to create requests
        if (config.get(ConfigProperties.Requests.Games.AllowGuestsToRequestGames) != true && currentUser == null) {
            throw EndpointException("Only registered users can submit game requests")
        }

        // Check if user has too many open requests (0 means no limit per user)
        // Note: All guests are treated as a single user with null ID and thus share their request limit
        // Note: Admins are exempt from this limit
        val pendingRequestsForUser = gameRequestRepository.findRequestsByRequesterIdAndStatusIn(
            currentUser?.id,
            listOf(GameRequestStatus.PENDING)
        )
        val maxRequestsPerUser = config.get(ConfigProperties.Requests.Games.MaxOpenRequestsPerUser) ?: 0
        if (maxRequestsPerUser != 0 && auth?.isAdmin() != true && pendingRequestsForUser.size >= maxRequestsPerUser) {
            throw EndpointException("You have reached the maximum number of pending requests (${maxRequestsPerUser})")
        }

        val newGameRequest = GameRequest(
            title = gameRequest.title,
            release = gameRequest.release,
            platform = gameRequest.platform,
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
        // Note: Requests submitted by guests (request is null) can only be deleted by an admin
        if (auth?.isAdmin() != true && (requester == null || requester.id != currentUser?.id)) {
            throw EndpointException("Only the requester or an admin can delete a game request")
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

    private fun completeMatchingRequests(game: Game) {
        val gameTitle = game.title
        val gameRelease = game.release
        val gamePlatforms = game.platforms

        if (gameTitle == null) {
            log.warn { "Game '${game.id}' is missing title, cannot complete matching requests" }
            return
        }

        // Check each platform of the game for matching requests
        gamePlatforms.forEach { platform ->
            val matchingRequests = gameRequestRepository.findRequestsByTitleAndReleaseYearAndPlatformAndStatusNotIn(
                gameTitle,
                gameRelease,
                platform,
                listOf(GameRequestStatus.FULFILLED)
            )

            matchingRequests.forEach { request ->
                request.status = GameRequestStatus.FULFILLED
                request.linkedGameId = game.id
                val persistedRequest = gameRequestRepository.save(request)
                emit(GameRequestEvent.Updated(persistedRequest.toDto()))
                log.info { "Marked game request '${request.title}' (${request.release?.atZone(ZoneId.systemDefault())?.year}) for ${request.platform} as FULFILLED because game is now available" }
            }
        }
    }

}