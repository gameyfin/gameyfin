package org.gameyfin.app.requests.dto

import org.gameyfin.app.requests.status.GameRequestStatus
import org.gameyfin.app.users.dto.UserInfoAdminDto
import java.time.Instant

class GameRequestDto(
    val id: Long,
    val title: String,
    val release: Instant,
    val status: GameRequestStatus,
    val requester: UserInfoAdminDto
)