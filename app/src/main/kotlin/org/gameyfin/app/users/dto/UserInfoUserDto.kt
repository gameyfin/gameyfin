package org.gameyfin.app.users.dto

data class UserInfoUserDto(
    val username: String,
    val hasAvatar: Boolean,
    val avatarId: Long? = null,
)