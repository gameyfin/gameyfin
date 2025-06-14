package org.gameyfin.app.users.util

import org.gameyfin.app.core.Role
import org.gameyfin.app.users.entities.User
import org.springframework.security.core.userdetails.UserDetails

fun User.hasRole(role: Role): Boolean {
    return role.roleName in this.roles.map { r -> r.roleName }
}

fun UserDetails.hasRole(role: Role): Boolean {
    return role.roleName in this.authorities.map { a -> a.authority }
}

fun UserDetails.isAdmin(): Boolean {
    return hasRole(Role.SUPERADMIN) || hasRole(Role.ADMIN)
}