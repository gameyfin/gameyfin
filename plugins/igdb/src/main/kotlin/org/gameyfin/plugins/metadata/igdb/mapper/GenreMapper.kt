package org.gameyfin.plugins.metadata.igdb.mapper

import org.gameyfin.pluginapi.gamemetadata.Genre
import org.slf4j.LoggerFactory

class GenreMapper {
    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)

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
    }
}