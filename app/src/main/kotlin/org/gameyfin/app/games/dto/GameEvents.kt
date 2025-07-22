package org.gameyfin.app.games.dto


sealed interface GameEvent {
    val type: String
}

sealed class GameUserEvent : GameEvent {
    data class Created(val game: GameUserDto, override val type: String = "created") : GameUserEvent()
    data class Updated(val game: GameUserDto, override val type: String = "updated") : GameUserEvent()
    data class Deleted(val gameId: Long, override val type: String = "deleted") : GameUserEvent()
}

sealed class GameAdminEvent : GameEvent {
    data class Created(val game: GameAdminDto, override val type: String = "created") : GameAdminEvent()
    data class Updated(val game: GameAdminDto, override val type: String = "updated") : GameAdminEvent()
    data class Deleted(val gameId: Long, override val type: String = "deleted") : GameAdminEvent()
}