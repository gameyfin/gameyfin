package de.grimsi.gameyfin.users.dto

data class UserInfoDto(
    val username: String,
    val managedBySso: Boolean,
    val email: String,
    val emailConfirmed: Boolean,
    var roles: List<String>
)