package de.grimsi.gameyfin.users

import de.grimsi.gameyfin.config.Roles
import de.grimsi.gameyfin.users.entities.Role
import de.grimsi.gameyfin.users.persistence.RoleRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service

@Service
@Transactional
class RoleService(
    private val roleRepository: RoleRepository
) {
    /**
     * @return the number of registered users with a given role
     * @return 0 if a role does not exist
     */
    fun getUserCountForRole(role: Roles): Int {
        val r = roleRepository.findByRolename(role.roleName) ?: return 0
        return r.users.size
    }

    fun toRoles(roles: Collection<String>): List<Role> {
        return roles.mapNotNull { r -> roleRepository.findByRolename(r) }
    }

    fun toRole(role: String): Role {
        return roleRepository.findByRolename(role) ?: throw RuntimeException("Role $role does not exist")
    }
}