package de.grimsi.gameyfin.core

import com.fasterxml.jackson.annotation.JsonValue
import de.grimsi.gameyfin.users.RoleService.Companion.INTERNAL_ROLE_PREFIX

enum class Role(val roleName: String, val powerLevel: Int) {

    SUPERADMIN(Names.SUPERADMIN, 3),
    ADMIN(Names.ADMIN, 2),
    USER(Names.USER, 1);

    @JsonValue
    override fun toString(): String {
        return this.roleName
    }

    companion object {
        fun safeValueOf(type: String): Role? {
            val enumString = type.removePrefix(INTERNAL_ROLE_PREFIX)
            return java.lang.Enum.valueOf(Role::class.java, enumString)
        }
    }

    // necessary for the ability to use the Roles class in the @RolesAllowed annotation
    class Names {
        companion object {
            const val SUPERADMIN = "${INTERNAL_ROLE_PREFIX}SUPERADMIN"
            const val ADMIN = "${INTERNAL_ROLE_PREFIX}ADMIN"
            const val USER = "${INTERNAL_ROLE_PREFIX}USER"
        }
    }
}