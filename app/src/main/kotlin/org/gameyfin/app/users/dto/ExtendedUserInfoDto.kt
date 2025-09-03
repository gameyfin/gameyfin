package org.gameyfin.app.users.dto

import org.gameyfin.app.core.Role

data class ExtendedUserInfoDto(
    val id: Long,
    val username: String,
    val managedBySso: Boolean,
    val email: String,
    val emailConfirmed: Boolean,
    val enabled: Boolean,
    val hasAvatar: Boolean,
    val avatarId: Long? = null,
    var roles: List<Role>
)