package de.grimsi.gameyfin.games

import de.grimsi.gameyfin.core.plugins.management.PluginManagementService
import de.grimsi.gameyfin.games.dto.GameDto
import de.grimsi.gameyfin.games.entities.*
import de.grimsi.gameyfin.games.repositories.CompanyRepository
import de.grimsi.gameyfin.games.repositories.GameRepository
import de.grimsi.gameyfin.games.repositories.ImageContentStore
import de.grimsi.gameyfin.games.repositories.ImageRepository
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
    private val imageRepository: ImageRepository,
    private val imageContentStore: ImageContentStore
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

    fun createFromFile(path: Path): GameDto {
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

        var game = toEntity(metadata, path, plugin)
        game = createOrUpdate(game)

        return toDto(game)
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
            title = game.title,
            coverImageUrl = game.coverImage.contentId,
            comment = game.comment,
            summary = game.summary,
            release = game.release,
            publishers = game.publishers.map { it.name },
            developers = game.developers.map { it.name },
            genres = game.genres.map { it.name },
            themes = game.themes.map { it.name },
            keywords = game.keywords.toList(),
            features = game.features.map { it.name },
            perspectives = game.perspectives.map { it.name },
            images = game.images.mapNotNull { it.id },
            videoUrls = game.videoUrls.map { it.toString() },
            source = game.source.pluginId
        )
    }

    private fun toEntity(metadata: GameMetadata, path: Path, source: GameMetadataProvider): Game {
        return Game(
            title = metadata.title,
            summary = metadata.description,
            coverImage = downloadAndPersist(metadata.coverUrl, ImageType.COVER),
            release = metadata.release,
            publishers = metadata.publishedBy.map { toEntity(it, CompanyType.PUBLISHER) }.toSet(),
            developers = metadata.developedBy.map { toEntity(it, CompanyType.DEVELOPER) }.toSet(),
            genres = metadata.genres,
            themes = metadata.themes,
            keywords = metadata.keywords,
            features = metadata.features,
            perspectives = metadata.perspectives,
            images = metadata.screenshotUrls.map { downloadAndPersist(it, ImageType.SCREENSHOT) }.toSet(),
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

    private fun downloadAndPersist(imageUrl: URL, type: ImageType): Image {
        imageRepository.findByOriginalUrl(imageUrl)?.let { return it }

        val image = Image(originalUrl = imageUrl, type = type)
        imageUrl.openStream().use { input ->
            image.mimeType = URLConnection.guessContentTypeFromStream(input)
            imageContentStore.setContent(image, input)
        }
        return imageRepository.save(image)
    }
}