package org.gameyfin.app.core

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import org.gameyfin.app.users.RoleService
import java.lang.Enum
import kotlin.IllegalArgumentException
import kotlin.Int
import kotlin.String

enum class Role(val roleName: String, val powerLevel: Int) {

    SUPERADMIN(Names.SUPERADMIN, 3),
    ADMIN(Names.ADMIN, 2),
    USER(Names.USER, 1);

    @JsonValue
    override fun toString(): String {
        return this.roleName
    }

    companion object {

        @JsonCreator
        @JvmStatic
        fun fromValue(value: String): Role? {
            val enumString = value.removePrefix(RoleService.INTERNAL_ROLE_PREFIX)
            return entries.find { it.roleName == enumString }
        }

        fun safeValueOf(type: String?): Role? {
            if (type == null) return null
            val enumString = type.removePrefix(RoleService.INTERNAL_ROLE_PREFIX)
            return try {
                Enum.valueOf(Role::class.java, enumString)
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }

    // necessary for the ability to use the Roles class in the @RolesAllowed annotation
    class Names {
        companion object {
            const val SUPERADMIN = "${RoleService.INTERNAL_ROLE_PREFIX}SUPERADMIN"
            const val ADMIN = "${RoleService.INTERNAL_ROLE_PREFIX}ADMIN"
            const val USER = "${RoleService.INTERNAL_ROLE_PREFIX}USER"
        }
    }
}