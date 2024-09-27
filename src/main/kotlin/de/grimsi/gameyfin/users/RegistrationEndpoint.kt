package de.grimsi.gameyfin.users

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Roles
import de.grimsi.gameyfin.users.dto.UserRegistrationDto
import jakarta.annotation.security.RolesAllowed

@AnonymousAllowed
@Endpoint
class RegistrationEndpoint(
    private val userService: UserService
) {
    fun isSelfRegistrationAllowed(): Boolean {
        return userService.selfRegistrationAllowed
    }

    fun registerUser(registration: UserRegistrationDto) {
        userService.selfRegisterUser(registration)

        // No return value to prevent enumeration attacks
    }

    fun isUsernameAvailable(username: String): Boolean {
        return !userService.existsByUsername(username)
    }

    @RolesAllowed(Roles.Names.ADMIN)
    fun confirmRegistration(username: String) {
        userService.confirmRegistration(username)
    }
}