package org.gameyfin.app.core.security

import org.gameyfin.app.core.Role
import org.springframework.security.core.context.SecurityContextHolder

fun isCurrentUserAdmin(): Boolean {
    return SecurityContextHolder.getContext().authentication?.authorities?.any { it.authority == Role.Names.ADMIN || it.authority == Role.Names.SUPERADMIN }
        ?: false
}