package de.grimsi.gameyfin.meta

enum class Roles(val roleName: String) {
    SUPERADMIN(Names.SUPERADMIN),
    ADMIN(Names.ADMIN),
    USER(Names.USER);

    // necessary for the ability to use the Roles class in the @RolesAllowed annotation
    class Names {
        companion object {
            const val SUPERADMIN = "ROLE_SUPERADMIN"
            const val ADMIN = "ROLE_ADMIN"
            const val USER = "ROLE_USER"
        }
    }
}