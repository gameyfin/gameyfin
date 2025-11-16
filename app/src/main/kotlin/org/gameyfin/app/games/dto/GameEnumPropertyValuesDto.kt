package org.gameyfin.app.games.dto

import org.gameyfin.pluginapi.gamemetadata.GameFeature
import org.gameyfin.pluginapi.gamemetadata.Genre
import org.gameyfin.pluginapi.gamemetadata.PlayerPerspective
import org.gameyfin.pluginapi.gamemetadata.Theme

data class GameEnumPropertyValuesDto(
    val genres: List<Genre>,
    val themes: List<Theme>,
    val features: List<GameFeature>,
    val perspectives: List<PlayerPerspective>
)
