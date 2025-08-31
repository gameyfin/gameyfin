package org.gameyfin.app.requests.extensions

import org.gameyfin.app.requests.GameRequest
import org.gameyfin.app.requests.dto.GameRequestDto

fun GameRequest.toDto(): GameRequestDto {
    return GameRequestDto(
        id = this.id!!,
        title = this.title,
        release = this.release,
        externalProviderIds = this.externalProviderIds,
        status = this.status,
        requester = this.requester.toDto()
    )
}