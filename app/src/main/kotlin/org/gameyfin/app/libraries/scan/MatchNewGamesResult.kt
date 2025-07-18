package org.gameyfin.app.libraries.scan

import org.gameyfin.app.games.entities.Game

data class MatchNewGamesResult(
    val unmatchedPaths: List<String>,
    val matchedGames: List<Game>
)