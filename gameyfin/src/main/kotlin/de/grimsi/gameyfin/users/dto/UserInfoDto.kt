package de.grimsi.gameyfin.users.dto

import de.grimsi.gameyfin.core.Role

data class UserInfoDto(
    val username: String,
    val managedBySso: Boolean,
    val email: String,
    val emailConfirmed: Boolean,
    val isEnabled: Boolean,
    val hasAvatar: Boolean,
    val avatarId: Long? = null,
    var roles: Set<Role>
)