package de.grimsi.gameyfin.plugins.steamgriddb

import de.grimsi.gameyfin.pluginapi.core.config.*
import de.grimsi.gameyfin.pluginapi.core.wrapper.ConfigurableGameyfinPlugin
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadata
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadataProvider
import de.grimsi.gameyfin.plugins.steamgriddb.api.SteamGridDbApiClient
import de.grimsi.gameyfin.plugins.steamgriddb.dto.SteamGridDbGame
import de.grimsi.gameyfin.plugins.steamgriddb.dto.SteamGridDbGrid
import kotlinx.coroutines.runBlocking
import org.pf4j.Extension
import org.pf4j.PluginWrapper
import java.net.URI

class SteamGridDbPlugin(wrapper: PluginWrapper) : ConfigurableGameyfinPlugin(wrapper) {

    companion object {
        private var client: SteamGridDbApiClient? = null
    }

    override val configMetadata: PluginConfigMetadata = listOf(
        ConfigMetadata(
            key = "apiKey",
            type = String::class.java,
            label = "SteamGridDB API key",
            description = "The API key can be obtained from your SteamGridDB account preferences",
            isSecret = true
        )
    )

    override fun validateConfig(config: Map<String, String?>): PluginConfigValidationResult {
        val pluginConfigValidationResult = super.validateConfig(config)

        if (pluginConfigValidationResult.result == PluginConfigValidationResultType.INVALID) {
            return pluginConfigValidationResult
        }

        try {
            val apiKeyToValidate = config["apiKey"]
            runBlocking { authenticate(apiKeyToValidate) }
            return PluginConfigValidationResult.VALID
        } catch (e: PluginConfigError) {
            log.error(e.message)
            return PluginConfigValidationResult.INVALID(
                mapOf("apiKey" to "Invalid API key")
            )
        }
    }

    override fun start() {
        try {
            runBlocking { authenticate(config("apiKey")) }
        } catch (e: PluginConfigError) {
            log.error(e.message)
        }
    }

    private suspend fun authenticate(apiKey: String? = null) {
        log.debug("Authenticating on SteamGridDB API...")

        val apiKey: String = apiKey ?: throw PluginConfigError("SteamGridDB API key not set")
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