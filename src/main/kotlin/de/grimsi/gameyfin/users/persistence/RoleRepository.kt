package de.grimsi.gameyfin.users.persistence

import de.grimsi.gameyfin.users.entities.Role
import org.springframework.data.jpa.repository.JpaRepository

interface RoleRepository : JpaRepository<Role, Long> {
    fun findByRolename(roleName: String): Role?
}