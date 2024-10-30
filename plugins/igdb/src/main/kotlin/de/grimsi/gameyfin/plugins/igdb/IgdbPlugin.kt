package de.grimsi.gameyfin.plugins.igdb

import com.api.igdb.apicalypse.APICalypse
import com.api.igdb.request.IGDBWrapper
import com.api.igdb.request.TwitchAuthenticator
import com.api.igdb.request.games
import de.grimsi.gameyfin.pluginapi.core.PluginConfigError
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadata
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadataFetcher
import de.grimsi.gameyfin.pluginapi.gamemetadata.Genre
import de.grimsi.gameyfin.pluginapi.gamemetadata.Theme
import io.github.oshai.kotlinlogging.KotlinLogging
import org.pf4j.Extension
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import java.time.Instant
import kotlin.collections.filter

class IgdbPlugin(wrapper: PluginWrapper) : Plugin(wrapper) {

    private val log = KotlinLogging.logger {}

    companion object {
        val config: IgdbPluginConfig = IgdbPluginConfig(null, null)
    }

    override fun start() {
        authenticate()
    }

    override fun stop() {
        log.debug { "IgdbPlugin.stop()" }
    }

    private fun authenticate() {
        log.debug { "Authenticating on Twitch API..." }

        val clientId: String = config.clientId ?: throw PluginConfigError("Twitch Client ID not set")
        val clientSecret: String = config.clientSecret ?: throw PluginConfigError("Twitch Client Secret not set")

        val token = TwitchAuthenticator.requestTwitchToken(clientId, clientSecret)
            ?: throw PluginConfigError("Failed to authenticate on Twitch API")

        IGDBWrapper.setCredentials(clientId, token.access_token)

        log.debug { "Authentication successful" }
    }

    @Extension
    class IgdbMetadataFetcher : GameMetadataFetcher {
        private val log = KotlinLogging.logger {}

        override fun fetchMetadata(gameId: String): GameMetadata {
            val findGameByName = APICalypse()
                .fields("*")
                .limit(100)
                .search(gameId)

            val game = IGDBWrapper.games(findGameByName).filter { it.slug == gameId.lowercase() }.firstOrNull()
                ?: throw IllegalArgumentException("Could not match game with ID '$gameId'")

            return GameMetadata(
                title = game.name,
                description = game.summary,
                release = Instant.ofEpochSecond(game.firstReleaseDate.seconds),
                userRating = game.rating.toInt(),
                criticRating = game.aggregatedRating.toInt(),
                developedBy = game.involvedCompaniesList.filter { it.developer }.map { it.company.name },
                publishedBy = game.involvedCompaniesList.filter { it.publisher }.map { it.company.name },
                genres = game.genresList.map { mapGenre(it) },
                themes = game.themesList.map { mapTheme(it) },
                screenshotUrls = listOf(),
                videoUrls = listOf(),
                features = listOf(),
                perspectives = listOf()
            )
        }

        private fun mapGenre(genre: proto.Genre): Genre {
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
                    log.warn { "Unknown genre: ${genre.slug}" }
                    Genre.UNKNOWN
                }
            }
        }

        private fun mapTheme(theme: proto.Theme): Theme {
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
                    log.warn { "Unknown theme: ${theme.slug}" }
                    Theme.UNKNOWN
                }
            }
        }
    }
}