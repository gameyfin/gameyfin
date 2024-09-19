package de.grimsi.gameyfin.core

import de.grimsi.gameyfin.users.RoleService.Companion.INTERNAL_ROLE_PREFIX

enum class Roles(val roleName: String) {
    SUPERADMIN(Names.SUPERADMIN),
    ADMIN(Names.ADMIN),
    USER(Names.USER);

    // necessary for the ability to use the Roles class in the @RolesAllowed annotation
    class Names {
        companion object {
            const val SUPERADMIN = "${INTERNAL_ROLE_PREFIX}SUPERADMIN"
            const val ADMIN = "${INTERNAL_ROLE_PREFIX}ADMIN"
            const val USER = "${INTERNAL_ROLE_PREFIX}USER"
        }
    }
}