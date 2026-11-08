package org.gameyfin.app.users.persistence

import org.gameyfin.app.core.Role
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.Lock
import org.gameyfin.app.users.entities.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface UserRepository : JpaRepository<User, Long> {
    // Stable ordering serializes concurrent account deletions.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u ORDER BY u.id")
    fun findAllForDeletion(): List<User>

    fun existsByUsername(userName: String): Boolean
    fun existsByEmail(email: String): Boolean
    fun findByUsername(userName: String): User?
    fun findByEmail(email: String): User?
    fun findByOidcProviderId(oidcProviderId: String): User?
    fun countUserByRolesContains(role: Role): Int

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM User u WHERE u.avatar.id = :imageId")
    fun existsByAvatar(@Param("imageId") imageId: Long): Boolean

    @Query("SELECT u FROM User u WHERE u.avatar.id = :imageId")
    fun findAllByAvatar(@Param("imageId") imageId: Long): List<User>
}
