package de.grimsi.gameyfin.libraries

import de.grimsi.gameyfin.games.entities.Game

data class LibraryScanResult(
    val libraries: Set<Library>,
    val newGames: Set<Game>,
    val removedGames: Set<Game>,
    val newUnmatchedPaths: Set<String>
)
