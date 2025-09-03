package org.gameyfin.app.requests.extensions

import org.gameyfin.app.requests.dto.GameRequestDto
import org.gameyfin.app.requests.entities.GameRequest
import org.gameyfin.app.users.extensions.toUserInfoDto

fun GameRequest.toDto(): GameRequestDto {
    return GameRequestDto(
        id = this.id!!,
        title = this.title,
        release = this.release,
        status = this.status,
        requester = this.requester?.toUserInfoDto(),
        voters = this.voters.map { it.toUserInfoDto() },
        createdAt = this.createdAt!!,
        updatedAt = this.updatedAt!!
    )
}

fun Collection<GameRequest>.toDtos(): List<GameRequestDto> {
    return this.map { it.toDto() }
}