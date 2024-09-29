package de.grimsi.gameyfin.users

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Role
import de.grimsi.gameyfin.users.dto.UserInfoDto
import de.grimsi.gameyfin.users.dto.UserUpdateDto
import de.grimsi.gameyfin.users.enums.RoleAssignmentResult
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder


@Endpoint
class UserEndpoint(
    private val userService: UserService,
    private val roleService: RoleService
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

    @PermitAll
    fun updateUser(updates: UserUpdateDto) {
        val auth: Authentication = SecurityContextHolder.getContext().authentication
        userService.updateUser(auth.name, updates)
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun getAllUsers(): List<UserInfoDto> {
        return userService.getAllUsers()
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun updateUserByName(username: String, updates: UserUpdateDto) {
        userService.updateUser(username, updates)
    }

    @PermitAll
    fun deleteUser() {
        val auth: Authentication = SecurityContextHolder.getContext().authentication
        userService.deleteUser(auth.name)
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun deleteUserByName(username: String) {
        userService.deleteUser(username)
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun getAvailableRoles(): List<String> {
        return roleService.getAllRoles().map { it.roleName }
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun getRolesBelow(): List<String> {
        val auth: Authentication = SecurityContextHolder.getContext().authentication
        return roleService.getRolesBelowAuth(auth).map { it.roleName }
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun assignRoles(username: String, roles: List<String>): RoleAssignmentResult {
        return userService.assignRoles(username, roles)
    }
}