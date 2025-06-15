package org.gameyfin.plugins.metadata.igdb

import com.api.igdb.utils.ImageSize
import com.api.igdb.utils.ImageType
import com.api.igdb.utils.imageBuilder
import org.gameyfin.pluginapi.gamemetadata.GameFeature
import org.gameyfin.pluginapi.gamemetadata.Genre
import org.gameyfin.pluginapi.gamemetadata.PlayerPerspective
import org.gameyfin.pluginapi.gamemetadata.Theme
import org.slf4j.LoggerFactory
import proto.Cover
import proto.Game
import proto.GameVideo
import proto.Screenshot
import java.net.URI

class Mapper {
    companion object {
        private val log = LoggerFactory.getLogger(Mapper::class.java)

        fun genre(genre: proto.Genre): Genre {
            return when (genre.slug) {
                "pinball" -> Genre.PINBALL
                "adventure" -> Genre.ADVENTURE
                "indie" -> Genre.INDIE
                "arcade" -> Genre.ARCADE
                "visual-novel" -> Genre.VISUAL_NOVEL
                "card-and-board-game" -> Genre.CARD_AND_BOARD_GAME
                "moba" -> Genre.MOBA
                "point-and-click" -> Genre.POINT_AND_CLICK
                "fighting" -> Genre.FIGHTING
                "shooter" -> Genre.SHOOTER
                "music" -> Genre.MUSIC
                "platform" -> Genre.PLATFORM
                "puzzle" -> Genre.PUZZLE
                "racing" -> Genre.RACING
                "real-time-strategy-rts" -> Genre.REAL_TIME_STRATEGY
                "role-playing-rpg" -> Genre.ROLE_PLAYING
                "simulator" -> Genre.SIMULATOR
                "sport" -> Genre.SPORT
                "strategy" -> Genre.STRATEGY
                "turn-based-strategy-tbs" -> Genre.TURN_BASED_STRATEGY
                "tactical" -> Genre.TACTICAL
                "hack-and-slash-beat-em-up" -> Genre.HACK_AND_SLASH_BEAT_EM_UP
                "quiz-trivia" -> Genre.QUIZ_TRIVIA
                else -> {
                    log.warn("Unknown genre: {}", genre.slug)
                    Genre.UNKNOWN
                }
            }
        }

        fun theme(theme: proto.Theme): Theme {
            return when (theme.slug) {
                "action" -> Theme.ACTION
                "fantasy" -> Theme.FANTASY
                "horror" -> Theme.HORROR
                "sci-fi" -> Theme.SCIENCE_FICTION
                "science-fiction" -> Theme.SCIENCE_FICTION
                "mystery" -> Theme.MYSTERY
                "thriller" -> Theme.THRILLER
                "survival" -> Theme.SURVIVAL
                "historical" -> Theme.HISTORICAL
                "stealth" -> Theme.STEALTH
                "comedy" -> Theme.COMEDY
                "business" -> Theme.BUSINESS
                "drama" -> Theme.DRAMA
                "non-fiction" -> Theme.NON_FICTION
                "sandbox" -> Theme.SANDBOX
                "educational" -> Theme.EDUCATIONAL
                "kids" -> Theme.KIDS
                "open-world" -> Theme.OPEN_WORLD
                "warfare" -> Theme.WARFARE
                "party" -> Theme.PARTY
                "4x-explore-expand-exploit-and-exterminate" -> Theme.FOUR_X
                "erotic" -> Theme.EROTIC
                "romance" -> Theme.ROMANCE
                else -> {
                    log.warn("Unknown theme: {}", theme.slug)
                    Theme.UNKNOWN
                }
            }
        }

        fun playerPerspective(perspective: proto.PlayerPerspective): PlayerPerspective {
            return when (perspective.slug) {
                "first-person" -> PlayerPerspective.FIRST_PERSON
                "third-person" -> PlayerPerspective.THIRD_PERSON
                "bird-view-isometric" -> PlayerPerspective.BIRD_VIEW_ISOMETRIC
                "bird-view-slash-isometric" -> PlayerPerspective.BIRD_VIEW_ISOMETRIC
                "side-view" -> PlayerPerspective.SIDE_VIEW
                "text" -> PlayerPerspective.TEXT
                "auditory" -> PlayerPerspective.AUDITORY
                "virtual-reality" -> PlayerPerspective.VIRTUAL_REALITY
                else -> {
                    log.warn("Unknown player perspective: {}", perspective.slug)
                    PlayerPerspective.UNKNOWN
                }
            }
        }

        fun screenshot(screenshot: Screenshot): URI {
            return URI(imageBuilder(screenshot.imageId, ImageSize.FHD, ImageType.PNG))
        }

        fun cover(cover: Cover): URI? {
            if (cover.imageId.isEmpty()) return null
            return URI(imageBuilder(cover.imageId, ImageSize.COVER_BIG, ImageType.PNG))
        }

        fun video(video: GameVideo): URI {
            return URI("https://www.youtube.com/watch?v=${video.videoId}")
        }

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