package org.gameyfin.plugins.metadata.steam.mapper

import org.gameyfin.pluginapi.gamemetadata.Genre
import org.gameyfin.plugins.metadata.steam.dto.SteamGenre

class Mapper {
    companion object {
        fun genre(steamGenre: SteamGenre): Genre {
            return when (steamGenre.id) {
                1 -> Genre.ACTION
                2 -> Genre.STRATEGY
                25 -> Genre.ADVENTURE
                23 -> Genre.INDIE
                3 -> Genre.ROLE_PLAYING
                28 -> Genre.SIMULATOR
                29 -> Genre.MMO
                9 -> Genre.RACING
                18 -> Genre.SPORT
                37 -> Genre.UNKNOWN // Free to Play doesn't match any genre directly
                51 -> Genre.UNKNOWN // Animation & Modeling doesn't match any genre directly
                58 -> Genre.UNKNOWN // Video Production doesn't match any genre directly
                4 -> Genre.UNKNOWN // Casual doesn't match any genre directly
                73 -> Genre.UNKNOWN // Violent doesn't map directly to a genre
                72 -> Genre.UNKNOWN // Nudity doesn't match any genre directly
                70 -> Genre.UNKNOWN // Early Access doesn't map directly to a genre
                74 -> Genre.UNKNOWN // Gore doesn't match any genre directly
                57 -> Genre.UNKNOWN // Utilities doesn't match any genre directly
                52 -> Genre.UNKNOWN // Audio Production doesn't match any genre directly
                53 -> Genre.UNKNOWN // Design & Illustration doesn't match any genre directly
                59 -> Genre.UNKNOWN // Web Publishing doesn't map directly to a genre
                55 -> Genre.UNKNOWN // Photo Editing doesn't map directly to a genre
                54 -> Genre.UNKNOWN // Education doesn't match any genre directly
                56 -> Genre.UNKNOWN // Software Training doesn't map directly to a genre
                71 -> Genre.UNKNOWN // Sexual Content doesn't match any genre directly
                60 -> Genre.UNKNOWN // Game Development doesn't map directly to a genre
                50 -> Genre.UNKNOWN // Accounting doesn't map directly to a genre
                81 -> Genre.UNKNOWN // Documentary doesn't map directly to a genre
                84 -> Genre.UNKNOWN // Tutorial doesn't map directly to a genre
                else -> Genre.UNKNOWN
            }
        }
    }
}