package de.grimsi.gameyfin.games

import de.grimsi.gameyfin.core.plugins.management.PluginManagementService
import de.grimsi.gameyfin.games.entities.Company
import de.grimsi.gameyfin.games.entities.CompanyType
import de.grimsi.gameyfin.games.entities.Game
import de.grimsi.gameyfin.games.entities.Screenshot
import de.grimsi.gameyfin.games.repositories.CompanyRepository
import de.grimsi.gameyfin.games.repositories.GameRepository
import de.grimsi.gameyfin.games.repositories.ScreenshotContentStore
import de.grimsi.gameyfin.games.repositories.ScreenshotRepository
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadata
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadataProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.pf4j.PluginManager
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.net.URL
import java.net.URLConnection
import java.nio.file.Path

@Service
class GameService(
    private val pluginManager: PluginManager,
    private val pluginManagementService: PluginManagementService,
    private val gameRepository: GameRepository,
    private val companyRepository: CompanyRepository,
    private val screenshotRepository: ScreenshotRepository,
    private val screenshotContentStore: ScreenshotContentStore
) {
    private val log = KotlinLogging.logger {}

    private val metadataPlugins: List<GameMetadataProvider>
        get() = pluginManager.getExtensions(GameMetadataProvider::class.java)

    fun createOrUpdate(game: Game): Game {
        gameRepository.findByPath(game.path)?.let {
            game.id = it.id
        }
        return gameRepository.save(game)
    }

    fun createFromFile(path: Path): Game {
        val metadataResults: Map<GameMetadataProvider, GameMetadata?> = runBlocking {
            coroutineScope {
                metadataPlugins.associateWith {
                    async {
                        try {
                            it.fetchMetadata(path.fileName.toString())
                        } catch (e: Exception) {
                            log.error(e) { "Error fetching metadata with plugin ${it.javaClass.name}" }
                            null
                        }
                    }.await()
                }
            }
        }

        val (plugin, metadata) = metadataResults.entries.firstOrNull { it.value != null }
            ?: throw NoMatchException("Could not match game at $path")

        if (metadata == null) {
            throw NoMatchException("Plugin ${plugin.javaClass} returned invalid metadata for game at $path")
        }

        val game = toEntity(metadata, path, plugin)

        return createOrUpdate(game)
    }

    fun getAllGames(): Collection<GameDto> {
        val entities = gameRepository.findAll()
        return entities.map { toDto(it) }
    }

    fun delete(game: Game) {
        gameRepository.delete(game)
    }

    private fun getById(id: Long): Game {
        return gameRepository.findByIdOrNull(id) ?: throw IllegalArgumentException("Game with id $id not found")
    }

    private fun toDto(game: Game): GameDto {
        val gameId = game.id ?: throw IllegalArgumentException("Game ID is null")

        return GameDto(
            id = gameId,
            title = game.title
        )
    }

    private fun toEntity(metadata: GameMetadata, path: Path, source: GameMetadataProvider): Game {
        return Game(
            title = metadata.title,
            summary = metadata.description,
            release = metadata.release,
            publishers = metadata.publishedBy.map { toEntity(it, CompanyType.PUBLISHER) }.toSet(),
            developers = metadata.developedBy.map { toEntity(it, CompanyType.DEVELOPER) }.toSet(),
            genres = metadata.genres,
            themes = metadata.themes,
            keywords = metadata.keywords,
            features = metadata.features,
            perspectives = metadata.perspectives,
            screenshots = metadata.screenshotUrls.map { downloadAndPersist(it) }.toSet(),
            videoUrls = metadata.videoUrls,
            path = path.toString(),
            source = pluginManagementService.getPluginManagementEntry(source.javaClass)
        )
    }

    private fun toEntity(companyName: String, companyType: CompanyType): Company {
        companyRepository.findByNameAndType(companyName, companyType)?.let { return it }
        val company = Company(name = companyName, type = companyType)
        return companyRepository.save(company)
    }

    private fun downloadAndPersist(screenshotUrl: URL): Screenshot {
        screenshotRepository.findByOriginalUrl(screenshotUrl)?.let { return it }

        val screenshot = Screenshot(originalUrl = screenshotUrl)
        screenshotUrl.openStream().use { input ->
            val mimeType = URLConnection.guessContentTypeFromStream(input)
            screenshot.mimeType = mimeType
            screenshotContentStore.setContent(screenshot, input)
        }
        return screenshotRepository.save(screenshot)
    }
}