package de.grimsi.gameyfin.users.dto

data class UserInfoDto(
    val username: String,
    val managedBySso: Boolean,
    val email: String,
    var roles: List<String>
)