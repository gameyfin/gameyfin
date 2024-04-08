package de.grimsi.gameyfin.setup

import de.grimsi.gameyfin.config.Roles
import de.grimsi.gameyfin.users.RoleService
import org.springframework.stereotype.Service

@Service
class SetupService(
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
}