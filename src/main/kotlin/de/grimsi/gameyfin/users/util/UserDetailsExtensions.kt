@file:JvmName("Utils")
@file:JvmMultifileClass

package de.grimsi.gameyfin.users.util

import de.grimsi.gameyfin.config.Roles
import org.springframework.security.core.userdetails.UserDetails

fun UserDetails.hasRole(role: Roles): Boolean {
    return this.authorities.map { a -> a.authority }.contains(role.roleName)
}

fun UserDetails.isAdmin(): Boolean {
    return hasRole(Roles.SUPERADMIN) || hasRole(Roles.ADMIN)
}