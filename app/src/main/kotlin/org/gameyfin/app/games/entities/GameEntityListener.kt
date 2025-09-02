package org.gameyfin.app.games.entities

import jakarta.persistence.PostPersist
import jakarta.persistence.PostRemove
import jakarta.persistence.PostUpdate
import org.gameyfin.app.games.GameService
import org.gameyfin.app.games.dto.GameAdminEvent
import org.gameyfin.app.games.dto.GameUserEvent
import org.gameyfin.app.games.extensions.toAdminDto
import org.gameyfin.app.games.extensions.toUserDto
import org.gameyfin.app.requests.GameRequestService
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component

@Component
class GameEntityListener : ApplicationContextAware {

    companion object {
        private lateinit var applicationContext: ApplicationContext
    }

    override fun setApplicationContext(context: ApplicationContext) {
        applicationContext = context
    }

    private fun getGameRequestService(): GameRequestService {
        return applicationContext.getBean(GameRequestService::class.java)
    }

    @PostPersist
    fun created(game: Game) {
        GameService.emitUser(GameUserEvent.Created(game.toUserDto()))
        GameService.emitAdmin(GameAdminEvent.Created(game.toAdminDto()))

        // After a game is created, mark any matching game requests as FULFILLED
        getGameRequestService().completeMatchingRequests(game)
    }

    @PostUpdate
    fun updated(game: Game) {
        GameService.emitUser(GameUserEvent.Updated(game.toUserDto()))
        GameService.emitAdmin(GameAdminEvent.Updated(game.toAdminDto()))
    }

    @PostRemove
    fun deleted(game: Game) {
        GameService.emitUser(GameUserEvent.Deleted(game.id!!))
        GameService.emitAdmin(GameAdminEvent.Deleted(game.id!!))
    }
}