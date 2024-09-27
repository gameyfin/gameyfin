package de.grimsi.gameyfin.users

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Roles
import de.grimsi.gameyfin.users.dto.UserInfoDto
import de.grimsi.gameyfin.users.dto.UserUpdateDto
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder


@Endpoint
class UserEndpoint(
    private val userService: UserService
) {
    @PermitAll
    fun existsByMail(email: String): Boolean {
        return userService.existsByEmail(email)
    }

    @PermitAll
    fun getUserInfo(): UserInfoDto {
        val auth: Authentication = SecurityContextHolder.getContext().authentication
        return userService.getUserInfo(auth)
    }

    @RolesAllowed(Roles.Names.ADMIN)
    fun getAllUsers(): List<UserInfoDto> {
        return userService.getAllUsers()
    }

    @PermitAll
    fun updateUser(updates: UserUpdateDto) {
        val auth: Authentication = SecurityContextHolder.getContext().authentication
        userService.updateUser(auth.name, updates)
    }

    @RolesAllowed(Roles.Names.ADMIN)
    fun updateUserByName(username: String, updates: UserUpdateDto) {
        userService.updateUser(username, updates)
    }

    @PermitAll
    fun deleteUser() {
        val auth: Authentication = SecurityContextHolder.getContext().authentication
        userService.deleteUser(auth.name)
    }

    @RolesAllowed(Roles.Names.ADMIN)
    fun deleteUserByName(username: String) {
        userService.deleteUser(username)
    }
}