package org.gameyfin.app.users.entities

import jakarta.persistence.EntityManager
import jakarta.persistence.PostRemove
import jakarta.persistence.PreRemove
import jakarta.persistence.PostPersist
import jakarta.persistence.PostUpdate
import org.gameyfin.app.users.UserStateChanged
import org.gameyfin.app.users.dto.UserEvent
import org.gameyfin.app.users.extensions.toExtendedUserInfoDto
import org.gameyfin.app.core.Utils
import org.gameyfin.app.core.events.UserDeletedEvent
import org.gameyfin.app.requests.entities.GameRequest
import org.gameyfin.app.util.EntityManagerHolder
import org.gameyfin.app.util.EventPublisherHolder

class UserEntityListener {

    @PostPersist
    @PostUpdate
    fun changed(user: User) {
        EventPublisherHolder.publish(UserStateChanged(UserEvent(user.id!!, user.toExtendedUserInfoDto())))
    }

    @PreRemove
    fun preRemove(user: User) {
        val entityManager: EntityManager = EntityManagerHolder.getEntityManager()

        entityManager.createQuery("DELETE FROM UserPreference p WHERE p.id.userId = :userId")
            .setParameter("userId", user.id)
            .executeUpdate()

        // Remove user from all GameRequest voters and requester fields
        val gameRequests = entityManager.createQuery(
            "SELECT gr FROM GameRequest gr WHERE :user MEMBER OF gr.voters OR gr.requester = :user",
            GameRequest::class.java
        ).setParameter("user", user).resultList.toList()
        for (gr in gameRequests) {
            gr.voters.remove(user)
            if (gr.requester == user) gr.requester = null
            entityManager.merge(gr)
        }
    }

    // UserUpdateEvent triggered via {@link org.gameyfin.app.core.interceptors.EntityUpdateInterceptor#onFlushDirty}

    @PostRemove
    fun postRemove(user: User) {
        EventPublisherHolder.publish(UserStateChanged(UserEvent(user.id!!)))
        EventPublisherHolder.publish(UserDeletedEvent(this, user, Utils.getBaseUrl()))
    }
}
