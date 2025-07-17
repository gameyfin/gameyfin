package org.gameyfin.app.users.dto

import org.gameyfin.app.core.Role

data class UserInfoDto(
    val username: String,
    val managedBySso: Boolean,
    val email: String,
    val emailConfirmed: Boolean,
    val enabled: Boolean,
    val hasAvatar: Boolean,
    val avatarId: Long? = null,
    var roles: List<Role>
)