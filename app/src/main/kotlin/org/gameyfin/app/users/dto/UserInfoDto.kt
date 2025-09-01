package org.gameyfin.app.users.dto

data class UserInfoDto(
    val username: String,
    val hasAvatar: Boolean,
    val avatarId: Long? = null,
)