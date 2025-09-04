package org.gameyfin.app.requests.dto

sealed class GameRequestEvent {
    abstract val type: String

    data class Created(val gameRequest: GameRequestDto, override val type: String = "created") : GameRequestEvent()
    data class Updated(val gameRequest: GameRequestDto, override val type: String = "updated") : GameRequestEvent()
    data class Deleted(val gameRequestId: Long, override val type: String = "deleted") : GameRequestEvent()
}