package de.grimsi.gameyfin.security

import org.springframework.security.core.userdetails.UserDetails

fun UserDetails.hasRole(role: String): Boolean {
    return this.authorities.map { a -> a.authority }.contains("ROLE_".plus(role))
}

fun UserDetails.isAdmin(): Boolean {
    return hasRole("ADMIN")
}