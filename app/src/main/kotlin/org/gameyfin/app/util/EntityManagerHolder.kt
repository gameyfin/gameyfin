package org.gameyfin.app.util

import jakarta.persistence.EntityManager
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component

@Component
object EntityManagerHolder : ApplicationContextAware {
    private var entityManager: EntityManager? = null

    override fun setApplicationContext(context: ApplicationContext) {
        entityManager = context.getBean(EntityManager::class.java)
    }

    fun getEntityManager(): EntityManager {
        return entityManager ?: throw IllegalStateException("EntityManager not set")
    }
}