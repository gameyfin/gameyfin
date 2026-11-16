package org.gameyfin.app.users.dto

/** Full replacements make reconnects and repeated notifications idempotent. */
data class UserEvent(val id: Long, val user: ExtendedUserInfoDto? = null)
