package de.grimsi.gameyfin.users

import de.grimsi.gameyfin.config.Roles
import de.grimsi.gameyfin.users.dto.UserInfo
import de.grimsi.gameyfin.users.dto.UserRegistration
import de.grimsi.gameyfin.users.entities.User
import dev.hilla.Endpoint
import jakarta.annotation.security.PermitAll
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

@Endpoint
class UserEndpoint(
    private val userService: UserService
) {

    @PermitAll
    fun getUserInfo(): UserInfo {
        val auth: Authentication = SecurityContextHolder.getContext().authentication
        val authorities: List<String> = auth.authorities.map { g: GrantedAuthority -> g.authority }
        return UserInfo(username = auth.name, roles = authorities)
    }

    @PermitAll
    fun registerUser(registration: UserRegistration): UserInfo {
        val user: User = registerUser(registration, listOf(Roles.USER))
        return userService.toUserInfo(user)
    }

    private fun registerUser(registration: UserRegistration, roles: List<Roles>): User {
        val user = User(
            username = registration.username,
            password = registration.password,
            email = registration.email
        )

        return userService.registerUser(user, roles)
    }
}