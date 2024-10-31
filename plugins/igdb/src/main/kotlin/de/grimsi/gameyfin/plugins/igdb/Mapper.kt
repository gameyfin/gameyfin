package de.grimsi.gameyfin.plugins.igdb

import de.grimsi.gameyfin.pluginapi.gamemetadata.Genre
import de.grimsi.gameyfin.pluginapi.gamemetadata.Theme
import org.slf4j.LoggerFactory

class Mapper {
    companion object {
        private val log = LoggerFactory.getLogger(javaClass)

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
    }
}