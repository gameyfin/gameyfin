package org.gameyfin.app.libraries.scan

import org.gameyfin.app.games.entities.Game

data class UpdateLibraryResult(
    val removedGames: List<Game>
)