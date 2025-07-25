package org.gameyfin.app.users

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.PermitAll
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role
import org.gameyfin.app.users.dto.UserInfoDto
import org.gameyfin.app.users.dto.UserUpdateDto
import org.gameyfin.app.users.enums.RoleAssignmentResult
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder


@Endpoint
class UserEndpoint(
    private val userService: UserService,
    private val roleService: RoleService
) {
    @AnonymousAllowed
    fun getUserInfo(): UserInfoDto? {
        val auth = SecurityContextHolder.getContext().authentication
        if (!auth.isAuthenticated || auth.principal == "anonymousUser") return null
        return userService.getUserInfo()
    }

    @PermitAll
    fun updateUser(updates: UserUpdateDto) {
        val auth: Authentication = SecurityContextHolder.getContext().authentication
        userService.updateUser(auth.name, updates)
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun existsByMail(email: String): Boolean {
        return userService.existsByEmail(email)
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun getAllUsers(): List<UserInfoDto> {
        return userService.getAllUsers()
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun updateUserByName(username: String, updates: UserUpdateDto) {
        userService.updateUser(username, updates)
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun setUserEnabled(username: String, enabled: Boolean) {
        userService.setUserEnabled(username, enabled)
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
    fun canCurrentUserManage(username: String): Boolean {
        return userService.canManage(username)
    }

    @RolesAllowed(Role.Names.ADMIN)
    fun assignRoles(username: String, roles: List<String>): RoleAssignmentResult {
        return userService.assignRoles(username, roles)
    }
}