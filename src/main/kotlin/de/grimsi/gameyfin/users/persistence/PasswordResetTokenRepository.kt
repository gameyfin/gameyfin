package de.grimsi.gameyfin.users.persistence

import de.grimsi.gameyfin.users.entities.PasswordResetToken
import de.grimsi.gameyfin.users.entities.User
import org.springframework.data.jpa.repository.JpaRepository

interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, String> {
    fun findByToken(token: String): PasswordResetToken?
    fun findByUser(user: User): PasswordResetToken?
}