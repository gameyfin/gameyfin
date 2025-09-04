package org.gameyfin.app.users.entities

import jakarta.persistence.EntityManager
import jakarta.persistence.PreRemove
import org.gameyfin.app.requests.entities.GameRequest
import org.gameyfin.app.util.EntityManagerHolder

class UserEntityListener {

    @PreRemove
    fun preRemove(user: User) {
        val entityManager: EntityManager = EntityManagerHolder.getEntityManager()

        // Remove user from all GameRequest voters and requester fields
        val gameRequests = entityManager.createQuery(
            "SELECT gr FROM GameRequest gr WHERE :user MEMBER OF gr.voters OR gr.requester = :user",
            GameRequest::class.java
        ).setParameter("user", user).resultList
        for (gr in gameRequests) {
            gr.voters.remove(user)
            if (gr.requester == user) gr.requester = null
            entityManager.merge(gr)
        }
    }
}