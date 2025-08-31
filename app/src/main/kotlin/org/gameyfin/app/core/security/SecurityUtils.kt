package org.gameyfin.app.core.security

import org.gameyfin.app.core.Role
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder

fun getCurrentAuth(): Authentication {
    return SecurityContextHolder.getContext().authentication
}

fun isCurrentUserAdmin(): Boolean {
    return getCurrentAuth().authorities?.any { it.authority == Role.Names.ADMIN || it.authority == Role.Names.SUPERADMIN }
        ?: false
}