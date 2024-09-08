package de.grimsi.gameyfin.setup

import de.grimsi.gameyfin.meta.Roles
import de.grimsi.gameyfin.users.RoleService
import de.grimsi.gameyfin.users.UserService
import de.grimsi.gameyfin.users.entities.User
import org.springframework.stereotype.Service

@Service
class SetupService(
    private val userService: UserService,
    private val roleService: RoleService
) {

    /**
     * Checks if the minimal requirements to run Gameyfin are fulfilled
     * Currently these are:
     * 1. At least one user with "Super Admin" role
     */
    fun isSetupCompleted(): Boolean {
        return roleService.getUserCountForRole(Roles.SUPERADMIN) > 0
    }

    /**
     * Creates the initial user with Super-Admin permissions
     */
    fun createInitialAdminUser(superAdmin: User): User {
        return userService.registerUser(superAdmin, Roles.SUPERADMIN)
    }
}