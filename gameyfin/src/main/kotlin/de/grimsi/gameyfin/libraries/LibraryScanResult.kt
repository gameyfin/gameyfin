package de.grimsi.gameyfin.libraries

import de.grimsi.gameyfin.games.entities.Game

data class LibraryScanResult(
    val libraries: List<Library>,
    val newGames: List<Game>,
    val removedGames: List<Game>,
    val newUnmatchedPaths: List<String>
)
