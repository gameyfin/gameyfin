package org.gameyfin.app.libraries.scan

import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.libraries.entities.IgnoredPath

data class MatchNewGamesResult(
    val unmatchedPaths: List<IgnoredPath>,
    val matchedGames: List<Game>
)