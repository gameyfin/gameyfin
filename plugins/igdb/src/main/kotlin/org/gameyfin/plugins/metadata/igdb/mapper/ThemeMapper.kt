package org.gameyfin.plugins.metadata.igdb.mapper

import org.gameyfin.pluginapi.gamemetadata.Theme
import org.slf4j.LoggerFactory

class ThemeMapper {

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)

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
    }
}