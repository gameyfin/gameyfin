package org.gameyfin.app.core.interceptors

import org.gameyfin.app.core.events.GameUpdatedEvent
import org.gameyfin.app.core.events.UserUpdatedEvent
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.media.Image
import org.gameyfin.app.users.entities.User
import org.gameyfin.app.util.EventPublisherHolder
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.hibernate.Interceptor
import org.hibernate.type.Type
import org.springframework.stereotype.Component

@Component
class EntityUpdateInterceptor : Interceptor {

    override fun onFlushDirty(
        entity: Any?,
        id: Any?,
        currentState: Array<out Any?>?,
        previousState: Array<out Any?>?,
        propertyNames: Array<out String>?,
        types: Array<out Type>?
    ): Boolean {
        if (entity == null || currentState == null || previousState == null || propertyNames == null) {
            return false
        }

        when (entity) {
            is Game -> {
                val previousGame = reconstructGame(entity, previousState, propertyNames)
                val currentGame = reconstructGame(entity, currentState, propertyNames)
                EventPublisherHolder.publish(GameUpdatedEvent(this, previousGame, currentGame))
            }

            is User -> {
                val previousUser = reconstructUser(entity, previousState, propertyNames)
                val currentUser = reconstructUser(entity, currentState, propertyNames)
                EventPublisherHolder.publish(UserUpdatedEvent(this, previousUser, currentUser))
            }
        }

        return false
    }

    private fun reconstructGame(originalGame: Game, state: Array<out Any?>, propertyNames: Array<out String>): Game {
        val reconstructed = Game(
            library = originalGame.library,
            metadata = originalGame.metadata
        )

        for (i in propertyNames.indices) {
            when (propertyNames[i]) {
                "id" -> reconstructed.id = state[i] as? Long
                "createdAt" -> reconstructed.createdAt = state[i] as? java.time.Instant
                "updatedAt" -> reconstructed.updatedAt = state[i] as? java.time.Instant
                "title" -> reconstructed.title = state[i] as? String
                "platforms" -> {
                    @Suppress("UNCHECKED_CAST")
                    (state[i] as? MutableList<Platform>)?.let { reconstructed.platforms = it }
                }

                "coverImage" -> reconstructed.coverImage = state[i] as? Image
                "headerImage" -> reconstructed.headerImage = state[i] as? Image
                "comment" -> reconstructed.comment = state[i] as? String
                "summary" -> reconstructed.summary = state[i] as? String
                "release" -> reconstructed.release = state[i] as? java.time.Instant
                "userRating" -> reconstructed.userRating = state[i] as? Int
                "criticRating" -> reconstructed.criticRating = state[i] as? Int
                "images" -> {
                    @Suppress("UNCHECKED_CAST")
                    (state[i] as? MutableList<Image>)?.let { reconstructed.images = it }
                }
            }
        }

        return reconstructed
    }

    private fun reconstructUser(originalUser: User, state: Array<out Any?>, propertyNames: Array<out String>): User {
        val reconstructed = User(
            username = originalUser.username,
            email = originalUser.email
        )

        for (i in propertyNames.indices) {
            when (propertyNames[i]) {
                "id" -> reconstructed.id = state[i] as? Long
                "password" -> reconstructed.password = state[i] as? String
                "oidcProviderId" -> reconstructed.oidcProviderId = state[i] as? String
                "emailConfirmed" -> reconstructed.emailConfirmed = state[i] as? Boolean ?: false
                "enabled" -> reconstructed.enabled = state[i] as? Boolean ?: false
                "avatar" -> reconstructed.avatar = state[i] as? Image
                "roles" -> {
                    @Suppress("UNCHECKED_CAST")
                    (state[i] as? List<org.gameyfin.app.core.Role>)?.let { reconstructed.roles = it }
                }
            }
        }

        return reconstructed
    }
}
