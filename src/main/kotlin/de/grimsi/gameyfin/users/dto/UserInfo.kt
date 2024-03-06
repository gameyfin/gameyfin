package de.grimsi.gameyfin.users.dto

data class UserInfo(
    val name: String,
    val authorities: List<String>
)