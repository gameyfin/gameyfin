package de.grimsi.gameyfin.users.dto

import de.grimsi.gameyfin.meta.annotations.NullOrNotBlank

data class UserUpdateDto(
    @field:NullOrNotBlank
    val username: String?,
    @field:NullOrNotBlank
    val password: String?,
    @field:NullOrNotBlank
    val email: String?
)