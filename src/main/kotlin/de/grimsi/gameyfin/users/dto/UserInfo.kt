package de.grimsi.gameyfin.users.dto

data class UserInfo(
    val username: String,
    val email: String? = null,
    val roles: List<String>
)