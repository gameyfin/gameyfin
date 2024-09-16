package de.grimsi.gameyfin.users

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.meta.Roles
import de.grimsi.gameyfin.users.dto.UserInfoDto
import de.grimsi.gameyfin.users.dto.UserRegistrationDto
import de.grimsi.gameyfin.users.dto.UserUpdateDto
import de.grimsi.gameyfin.users.entities.User
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder


@Endpoint
class UserEndpoint(
    private val userService: UserService
) {

    @PermitAll
    fun getUserInfo(): UserInfoDto {
        val auth: Authentication = SecurityContextHolder.getContext().authentication
        return userService.getUserInfo(auth)
    }

    @RolesAllowed(Roles.Names.SUPERADMIN, Roles.Names.ADMIN)
    fun getAllUsers(): List<UserInfoDto> {
        return userService.getAllUsers()
    }

    @PermitAll
    fun registerUser(registration: UserRegistrationDto): UserInfoDto {
        val user: User = registerUser(registration, listOf(Roles.USER))
        return userService.toUserInfo(user)
    }

    @PermitAll
    fun updateUser(updates: UserUpdateDto) {
        val auth: Authentication = SecurityContextHolder.getContext().authentication
        userService.updateUser(auth.name, updates)
    }

    @RolesAllowed(Roles.Names.SUPERADMIN, Roles.Names.ADMIN)
    fun updateUserByName(username: String, updates: UserUpdateDto) {
        userService.updateUser(username, updates)
    }

    @PermitAll
    fun deleteUser() {
        val auth: Authentication = SecurityContextHolder.getContext().authentication
        userService.deleteUser(auth.name)
    }

    @RolesAllowed(Roles.Names.SUPERADMIN, Roles.Names.ADMIN)
    fun deleteUserByName(username: String) {
        userService.deleteUser(username)
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