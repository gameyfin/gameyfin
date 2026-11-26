package org.gameyfin.app.core.token

import org.gameyfin.app.users.entities.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface TokenRepository : JpaRepository<Token<*>, String> {
    @Query("SELECT t.creator.id FROM Token t WHERE t.secret = :secret")
    fun findCreatorIdBySecret(secret: String): Long?

    fun findBySecret(secret: String): Token<*>?
    fun <T : TokenType> findByCreatorAndType(creator: User, type: T): Token<T>?
    fun <T : TokenType> findByCreatorAndTypeAndPayload(creator: User, type: T, payload: Map<String, String>): Token<T>?
}