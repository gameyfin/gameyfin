package de.grimsi.gameyfin.users.dto

data class UserInfoDto(
    val username: String,
    val email: String,
    val roles: List<String>
)