package de.grimsi.gameyfin.games.dto

sealed class GameEvent {
    abstract val type: String

    data class Created(val game: GameDto, override val type: String = "created") : GameEvent()
    data class Updated(val game: GameDto, override val type: String = "updated") : GameEvent()
    data class Deleted(val gameId: Long, override val type: String = "deleted") : GameEvent()
}