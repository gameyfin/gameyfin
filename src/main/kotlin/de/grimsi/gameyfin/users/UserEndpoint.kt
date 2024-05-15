package de.grimsi.gameyfin.users

import de.grimsi.gameyfin.config.Roles
import de.grimsi.gameyfin.users.dto.UserInfo
import de.grimsi.gameyfin.users.dto.UserRegistration
import de.grimsi.gameyfin.users.entities.User
import dev.hilla.Endpoint
import jakarta.annotation.security.PermitAll
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.server.ResponseStatusException

@Endpoint
class UserEndpoint(
    private val userService: UserService,
    private val roleService: RoleService,
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

    @PermitAll
    fun registerInitialSuperAdmin(registration: UserRegistration): UserInfo {
        if (roleService.getUserCountForRole(Roles.SUPERADMIN) > 0) throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        val superAdmin: User = registerUser(registration, listOf(Roles.SUPERADMIN))
        return userService.toUserInfo(superAdmin)
    }

    private fun registerUser(registration: UserRegistration, roles: List<Roles>): User {
        val user = User(
            username = registration.username,
            password = registration.password,
            email = registration.email,
            roles = roles.map { r -> roleService.toRole(r.roleName) }
        )

        return userService.registerUser(user)
    }
}