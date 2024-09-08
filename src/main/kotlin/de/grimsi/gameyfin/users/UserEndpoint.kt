package de.grimsi.gameyfin.users

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.meta.Roles
import de.grimsi.gameyfin.users.dto.UserInfoDto
import de.grimsi.gameyfin.users.dto.UserRegistrationDto
import de.grimsi.gameyfin.users.entities.User
import jakarta.annotation.security.PermitAll
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder

@Endpoint
class UserEndpoint(
    private val userService: UserService
) {

    @PermitAll
    fun getUserInfo(): UserInfoDto {
        val auth: Authentication = SecurityContextHolder.getContext().authentication
        val authorities: List<String> = auth.authorities.map { g: GrantedAuthority -> g.authority }
        return UserInfoDto(username = auth.name, roles = authorities)
    }

    @PermitAll
    fun registerUser(registration: UserRegistrationDto): UserInfoDto {
        val user: User = registerUser(registration, listOf(Roles.USER))
        return userService.toUserInfo(user)
    }

    private fun registerUser(registration: UserRegistrationDto, roles: List<Roles>): User {
        val user = User(
            username = registration.username,
            password = registration.password,
            email = registration.email
        )

        return userService.registerUser(user, roles)
    }
}