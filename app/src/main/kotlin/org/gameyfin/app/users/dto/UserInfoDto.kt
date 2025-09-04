package org.gameyfin.app.users.dto

data class UserInfoDto(
    val id: Long,
    val username: String,
    val hasAvatar: Boolean,
    val avatarId: Long? = null,
)