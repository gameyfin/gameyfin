package de.grimsi.gameyfin.users.dto

data class UserInfoDto(
    val username: String,
    val email: String? = null,
    val roles: List<String>
)