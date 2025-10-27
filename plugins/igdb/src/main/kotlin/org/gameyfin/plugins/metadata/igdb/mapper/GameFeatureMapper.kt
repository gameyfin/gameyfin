package org.gameyfin.plugins.metadata.igdb.mapper

import org.gameyfin.pluginapi.gamemetadata.GameFeature
import org.slf4j.LoggerFactory
import proto.Game

class GameFeatureMapper {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)

        fun gameFeatures(game: Game): Set<GameFeature> {
            val gameFeatures = mutableSetOf<GameFeature>()

            // Get LAN support from multiplayer modes
            if (game.multiplayerModesList.any { it.lancoop }) gameFeatures.add(GameFeature.LOCAL_MULTIPLAYER)

            for (gameMode in game.gameModesList) {
                when (gameMode.slug) {
                    "single-player" -> gameFeatures.add(GameFeature.SINGLEPLAYER)
                    "multiplayer" -> gameFeatures.add(GameFeature.MULTIPLAYER)
                    "massively-multiplayer-online-mmo" -> gameFeatures.add(GameFeature.MULTIPLAYER)
                    "battle-royale" -> gameFeatures.add(GameFeature.MULTIPLAYER)
                    "co-operative" -> gameFeatures.add(GameFeature.CO_OP)
                    "split-screen" -> gameFeatures.add(GameFeature.SPLITSCREEN)
                    else -> {
                        log.warn("Unknown game mode: {}", gameMode.slug)
                    }
                }
            }
            return gameFeatures
        }
    }
}