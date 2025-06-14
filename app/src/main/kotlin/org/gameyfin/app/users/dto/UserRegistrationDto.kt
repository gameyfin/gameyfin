package org.gameyfin.app.users.dto

data class UserRegistrationDto(
    val username: String,
    val password: String,
    val email: String
)