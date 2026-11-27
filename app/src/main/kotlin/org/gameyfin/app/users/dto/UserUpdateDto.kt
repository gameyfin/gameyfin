package org.gameyfin.app.users.dto

import org.gameyfin.app.core.annotations.NullOrNotBlank

data class UserUpdateDto(
    @field:NullOrNotBlank
    val username: String?,
    @field:NullOrNotBlank
    val password: String?,
    // Omission leaves the address unchanged. A blank value requests removal.
    val email: String?
)