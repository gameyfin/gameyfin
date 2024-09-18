package de.grimsi.gameyfin.notifications.dto

data class EmailCredentialsDto(
    val host: String,
    val port: Int,
    val username: String,
    val password: String?
)