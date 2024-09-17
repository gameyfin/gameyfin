package de.grimsi.gameyfin.setup

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import com.vaadin.hilla.exception.EndpointException
import de.grimsi.gameyfin.meta.Roles
import de.grimsi.gameyfin.users.RoleService
import de.grimsi.gameyfin.users.UserService
import de.grimsi.gameyfin.users.dto.UserInfoDto
import de.grimsi.gameyfin.users.dto.UserRegistrationDto
import de.grimsi.gameyfin.users.entities.User

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
    fun registerSuperAdmin(superAdminRegistration: UserRegistrationDto): UserInfoDto {
        if (setupService.isSetupCompleted()) throw EndpointException("Setup already completed")

        val user = User(
            username = superAdminRegistration.username,
            password = superAdminRegistration.password,
            email = superAdminRegistration.email,
            roles = setOf(roleService.toRole(Roles.SUPERADMIN))
        )

        val superAdmin = setupService.createInitialAdminUser(user)
        return userService.toUserInfo(superAdmin)
    }
}