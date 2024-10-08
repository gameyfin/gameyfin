package de.grimsi.gameyfin.users.dto

data class UserRegistrationDto(
    val username: String,
    val password: String,
    val email: String
)