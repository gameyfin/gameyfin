package de.grimsi.gameyfin.plugins.steamgriddb

import de.grimsi.gameyfin.pluginapi.core.GameyfinPlugin
import de.grimsi.gameyfin.pluginapi.core.PluginConfigElement
import de.grimsi.gameyfin.pluginapi.core.PluginConfigError
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadata
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadataProvider
import de.grimsi.gameyfin.plugins.steamgriddb.api.SteamGridDbApiClient
import de.grimsi.gameyfin.plugins.steamgriddb.dto.SteamGridDbGame
import de.grimsi.gameyfin.plugins.steamgriddb.dto.SteamGridDbGrid
import kotlinx.coroutines.runBlocking
import org.pf4j.Extension
import org.pf4j.PluginWrapper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI

class SteamGridDbPlugin(wrapper: PluginWrapper) : GameyfinPlugin(wrapper) {

    companion object {
        private var client: SteamGridDbApiClient? = null
    }

    val log: Logger = LoggerFactory.getLogger(javaClass)

    override val configMetadata: List<PluginConfigElement> = listOf(
        PluginConfigElement("apiKey", "SteamGridDB API key", "Your SteamGridDB API key", true)
    )

    override fun validateConfig(config: Map<String, String?>): Boolean {
        try {
            runBlocking { authenticate() }
            return true
        } catch (e: PluginConfigError) {
            log.error(e.message)
            return false
        }
    }

    override fun start() {
        try {
            runBlocking { authenticate() }
        } catch (e: PluginConfigError) {
            log.error(e.message)
        }
    }

    private suspend fun authenticate() {
        log.debug("Authenticating on SteamGridDB API...")

        val apiKey: String = config["apiKey"] ?: throw PluginConfigError("SteamGridDB API key not set")
        val client = SteamGridDbApiClient(apiKey)

        if (!client.isApiKeyValid()) {
            throw PluginConfigError("Failed to authenticate on SteamGridDB API with provided credentials")
        }

        SteamGridDbPlugin.client = client
        log.debug("Authentication successful")
    }

    @Extension
    class SteamGridDBGameCoverProvider : GameMetadataProvider {

        override fun fetchMetadata(gameId: String, maxResults: Int): List<GameMetadata> {
            return runBlocking {
                var searchResults = searchSteamGridDb(gameId)

                if (searchResults.isEmpty()) return@runBlocking emptyList()
                if (searchResults.size > maxResults) searchResults = searchResults.slice(0 until maxResults)

                return@runBlocking searchResults
                    .map { game ->
                        GameMetadata(
                            originalId = game.id.toString(),
                            title = game.name,
                            coverUrl = getGridForGame(game.id)?.let { grid -> URI(grid.url) }
                        )
                    }
                    .filter { it.coverUrl != null }
            }
        }

        private suspend fun searchSteamGridDb(term: String): List<SteamGridDbGame> {
            val client = client ?: throw PluginConfigError("SteamGridDB API client not initialized")

            val searchResult = client.search(term)

            return if (searchResult.success && searchResult.data !== null) {
                searchResult.data
            } else {
                emptyList()
            }
        }

        private suspend fun getGridForGame(gameId: Int): SteamGridDbGrid? {
            val client = client ?: throw PluginConfigError("SteamGridDB API client not initialized")

            val gameDetails = client.grids(gameId)

            return gameDetails.data?.firstOrNull()
        }
    }
}