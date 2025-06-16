package org.gameyfin.plugins.metadata.steamgriddb

import kotlinx.coroutines.runBlocking
import org.gameyfin.pluginapi.core.config.*
import org.gameyfin.pluginapi.core.wrapper.ConfigurableGameyfinPlugin
import org.gameyfin.pluginapi.gamemetadata.GameMetadata
import org.gameyfin.pluginapi.gamemetadata.GameMetadataProvider
import org.gameyfin.plugins.metadata.steamgriddb.api.SteamGridDbApiClient
import org.gameyfin.plugins.metadata.steamgriddb.dto.SteamGridDbGame
import org.gameyfin.plugins.metadata.steamgriddb.dto.SteamGridDbGrid
import org.gameyfin.plugins.metadata.steamgriddb.dto.SteamGridDbHero
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

    @Extension(ordinal = 1)
    class SteamGridDBGameCoverProvider : GameMetadataProvider {

        override fun fetchByTitle(gameTitle: String, maxResults: Int): List<GameMetadata> {
            return runBlocking {
                val results = searchSteamGridDb(gameTitle)

                results.map { game ->
                    val grids = getGridsForGame(game.id)
                    val heroes = getHeroesForGame(game.id)
                    GameMetadata(
                        originalId = game.id.toString(),
                        title = game.name,
                        coverUrls = grids?.map { URI(it.url) },
                        headerUrls = heroes?.map { URI(it.url) }
                    )
                }.take(maxResults)
            }
        }

        override fun fetchById(id: String): GameMetadata? {
            return runBlocking {
                val gameId = id.toIntOrNull() ?: return@runBlocking null
                val game = getGameById(gameId) ?: return@runBlocking null

                val grids = getGridsForGame(game.id)
                val heroes = getHeroesForGame(game.id)

                return@runBlocking GameMetadata(
                    originalId = game.id.toString(),
                    title = game.name,
                    coverUrls = grids?.map { URI(it.url) },
                    headerUrls = heroes?.map { URI(it.url) }
                )
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

        private suspend fun getGridsForGame(gameId: Int): List<SteamGridDbGrid>? {
            val client = client ?: throw PluginConfigError("SteamGridDB API client not initialized")

            val gameDetails = client.grids(gameId)

            return gameDetails.data
        }

        private suspend fun getHeroesForGame(gameId: Int): List<SteamGridDbHero>? {
            val client = client ?: throw PluginConfigError("SteamGridDB API client not initialized")

            val gameDetails = client.heroes(gameId)

            return gameDetails.data
        }

        private suspend fun getGameById(gameId: Int): SteamGridDbGame? {
            val client = client ?: throw PluginConfigError("SteamGridDB API client not initialized")

            val gameDetails = client.game(gameId)

            return gameDetails.data
        }
    }
}