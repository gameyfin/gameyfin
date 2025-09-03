package org.gameyfin.app.setup

import org.gameyfin.app.core.Role
import org.gameyfin.app.users.RoleService
import org.gameyfin.app.users.UserService
import org.gameyfin.app.users.dto.ExtendedUserInfoDto
import org.gameyfin.app.users.dto.UserRegistrationDto
import org.gameyfin.app.users.entities.User
import org.gameyfin.app.users.extensions.toExtendedUserInfoDto
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
        return roleService.getUserCountForRole(Role.SUPERADMIN) > 0
    }

    /**
     * Creates the initial user with Super-Admin permissions
     */
    fun createInitialAdminUser(registration: UserRegistrationDto): ExtendedUserInfoDto {
        val superAdmin = User(
            username = registration.username,
            password = registration.password,
            email = registration.email,
            enabled = true,
            roles = listOf(Role.SUPERADMIN)
        )

        val user = userService.registerOrUpdateUser(superAdmin)
        return user.toExtendedUserInfoDto()
    }
}