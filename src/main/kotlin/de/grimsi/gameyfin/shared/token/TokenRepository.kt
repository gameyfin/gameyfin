package de.grimsi.gameyfin.shared.token

import de.grimsi.gameyfin.users.entities.User
import org.springframework.data.jpa.repository.JpaRepository

interface TokenRepository : JpaRepository<Token<*>, String> {
    fun findBySecret(secret: String): Token<*>?
    fun <T : TokenType> findByUserAndType(user: User, type: T): Token<T>?
}