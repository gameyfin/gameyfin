package de.grimsi.gameyfin.setup

import com.vaadin.flow.server.auth.AnonymousAllowed
import de.grimsi.gameyfin.config.Roles
import de.grimsi.gameyfin.users.RoleService
import de.grimsi.gameyfin.users.UserService
import de.grimsi.gameyfin.users.dto.UserInfo
import de.grimsi.gameyfin.users.dto.UserRegistration
import de.grimsi.gameyfin.users.entities.User
import com.vaadin.hilla.Endpoint
import com.vaadin.hilla.exception.EndpointException

@Endpoint
class SetupEndpoint(
    private val setupService: SetupService,
    private val roleService: RoleService,
    private val userService: UserService
) {
    @AnonymousAllowed
    fun isSetupCompleted(): Boolean {
        return setupService.isSetupCompleted()
    }

    @AnonymousAllowed
    fun registerSuperAdmin(superAdminRegistration: UserRegistration): UserInfo {
        if (setupService.isSetupCompleted()) throw EndpointException("Setup already completed")

        val user = User(
            username = superAdminRegistration.username,
            password = superAdminRegistration.password,
            email = superAdminRegistration.email,
            roles = listOf(roleService.toRole(Roles.SUPERADMIN))
        )

        val superAdmin = setupService.createInitialAdminUser(user)
        return userService.toUserInfo(superAdmin)
    }
}