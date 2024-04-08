package de.grimsi.gameyfin.users.dto

data class UserRegistration(
    val username: String,
    val password: String,
    val email: String,
    val roles: List<String>
)