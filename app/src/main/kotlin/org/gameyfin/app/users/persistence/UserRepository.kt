package org.gameyfin.app.users.persistence

import org.gameyfin.app.core.Role
import org.gameyfin.app.users.entities.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun existsByUsername(userName: String): Boolean
    fun existsByEmail(email: String): Boolean
    fun findByUsername(userName: String): User?
    fun findByEmail(email: String): User?
    fun findByOidcProviderId(oidcProviderId: String): User?
    fun countUserByRolesContains(role: Role): Int
}