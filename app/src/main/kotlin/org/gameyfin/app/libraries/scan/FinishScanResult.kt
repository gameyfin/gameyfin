package org.gameyfin.app.libraries.scan

import org.gameyfin.app.games.entities.Game

data class FinishScanResult(
    val persistedGames: List<Game>,
)