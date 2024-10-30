package de.grimsi.gameyfin.plugins.igdb.dto

data class TwitchOAuthTokenDto(
    val accessToken: String,
    val expiresIn: Int,
    val tokenType: String
)