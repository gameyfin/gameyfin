@file:JvmName("Utils")
@file:JvmMultifileClass

package de.grimsi.gameyfin.users.util

import de.grimsi.gameyfin.config.Roles
import org.springframework.security.core.userdetails.UserDetails

fun UserDetails.hasRole(role: Roles): Boolean {
    return role.roleName in this.authorities.map { a -> a.authority }
}

fun UserDetails.isAdmin(): Boolean {
    return hasRole(Roles.SUPERADMIN) || hasRole(Roles.ADMIN)
}