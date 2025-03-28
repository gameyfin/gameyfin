package de.grimsi.gameyfin.games

import de.grimsi.gameyfin.core.plugins.management.PluginManagementService
import de.grimsi.gameyfin.games.dto.GameDto
import de.grimsi.gameyfin.games.dto.GameMetadataDto
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
import java.net.URI
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
        val metadataResults = queryPlugins(path.fileName.toString())
        val validResults = metadataResults.filterValues { it != null }
        if (validResults.isEmpty()) {
            throw NoMatchException("Could not match game at $path")
        }

        val sortedResults = validResults.entries.sortedByDescending {
            pluginManagementService.getPluginManagementEntry(it.key.javaClass).priority
        }

        val mergedGame = mergeResults(sortedResults, path)
        val savedGame = createOrUpdate(mergedGame)

        return toDto(savedGame)
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

    /**
     * Queries all metadata plugins for metadata on the provided game title
     * Runs the queries concurrently and asynchronously
     * @return A map of metadata plugins and their respective results
     */
    private fun queryPlugins(gameTitle: String): Map<GameMetadataProvider, GameMetadata?> {
        return runBlocking {
            coroutineScope {
                metadataPlugins.associateWith {
                    async {
                        try {
                            it.fetchMetadata(gameTitle)
                        } catch (e: Exception) {
                            log.error(e) { "Error fetching metadata with plugin ${it.javaClass.name}" }
                            null
                        }
                    }.await()
                }
            }
        }
    }

    private fun mergeResults(results: List<Map.Entry<GameMetadataProvider, GameMetadata?>>, path: Path): Game {
        val mergedGame = Game(path = path.toString())
        val metadataMap = mutableMapOf<String, FieldMetadata>()

        // Sort results by plugin priority
        val sortedResults = results.sortedByDescending {
            pluginManagementService.getPluginManagementEntry(it.key.javaClass).priority
        }

        sortedResults.forEach { (provider, metadata) ->
            val sourcePlugin = pluginManagementService.getPluginManagementEntry(provider.javaClass)
            metadata?.let {
                it.title.takeIf { it.isNotBlank() }?.let { title ->
                    if (!metadataMap.containsKey("title")) {
                        mergedGame.title = title
                        metadataMap["title"] = FieldMetadata(sourcePlugin)
                    }
                }
                it.description?.takeIf { it.isNotBlank() }?.let { description ->
                    if (!metadataMap.containsKey("summary")) {
                        mergedGame.summary = description
                        metadataMap["summary"] = FieldMetadata(sourcePlugin)
                    }
                }
                it.coverUrl?.let { coverUrl ->
                    if (!metadataMap.containsKey("coverImage")) {
                        mergedGame.coverImage = downloadAndPersist(coverUrl, ImageType.COVER)
                        metadataMap["coverImage"] = FieldMetadata(sourcePlugin)
                    }
                }
                it.release?.let { release ->
                    if (!metadataMap.containsKey("release")) {
                        mergedGame.release = release
                        metadataMap["release"] = FieldMetadata(sourcePlugin)
                    }
                }
                it.publishedBy?.takeIf { it.isNotEmpty() }?.let { publishedBy ->
                    if (!metadataMap.containsKey("publishers")) {
                        mergedGame.publishers =
                            publishedBy.map { name -> toEntity(name, CompanyType.PUBLISHER) }.toSet()
                        metadataMap["publishers"] = FieldMetadata(sourcePlugin)
                    }
                }
                it.developedBy?.takeIf { it.isNotEmpty() }?.let { developedBy ->
                    if (!metadataMap.containsKey("developers")) {
                        mergedGame.developers =
                            developedBy.map { name -> toEntity(name, CompanyType.DEVELOPER) }.toSet()
                        metadataMap["developers"] = FieldMetadata(sourcePlugin)
                    }
                }
                it.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                    if (!metadataMap.containsKey("genres")) {
                        mergedGame.genres = genres
                        metadataMap["genres"] = FieldMetadata(sourcePlugin)
                    }
                }
                it.themes?.takeIf { it.isNotEmpty() }?.let { themes ->
                    if (!metadataMap.containsKey("themes")) {
                        mergedGame.themes = themes
                        metadataMap["themes"] = FieldMetadata(sourcePlugin)
                    }
                }
                it.keywords?.takeIf { it.isNotEmpty() }?.let { keywords ->
                    if (!metadataMap.containsKey("keywords")) {
                        mergedGame.keywords = keywords
                        metadataMap["keywords"] = FieldMetadata(sourcePlugin)
                    }
                }
                it.features?.takeIf { it.isNotEmpty() }?.let { features ->
                    if (!metadataMap.containsKey("features")) {
                        mergedGame.features = features
                        metadataMap["features"] = FieldMetadata(sourcePlugin)
                    }
                }
                it.perspectives?.takeIf { it.isNotEmpty() }?.let { perspectives ->
                    if (!metadataMap.containsKey("perspectives")) {
                        mergedGame.perspectives = perspectives
                        metadataMap["perspectives"] = FieldMetadata(sourcePlugin)
                    }
                }
                it.screenshotUrls?.takeIf { it.isNotEmpty() }?.let { screenshotUrls ->
                    if (!metadataMap.containsKey("images")) {
                        mergedGame.images =
                            screenshotUrls.map { url -> downloadAndPersist(url, ImageType.SCREENSHOT) }.toSet()
                        metadataMap["images"] = FieldMetadata(sourcePlugin)
                    }
                }
                it.videoUrls?.takeIf { it.isNotEmpty() }?.let { videoUrls ->
                    if (!metadataMap.containsKey("videoUrls")) {
                        mergedGame.videoUrls = videoUrls
                        metadataMap["videoUrls"] = FieldMetadata(sourcePlugin)
                    }
                }
            }
        }

        mergedGame.metadata = metadataMap
        return mergedGame
    }

    private fun toDto(game: Game): GameDto {
        val gameId = game.id ?: throw IllegalArgumentException("Game ID is null")

        return GameDto(
            id = gameId,
            title = game.title!!,
            coverId = game.coverImage?.id,
            comment = game.comment,
            summary = game.summary,
            release = game.release,
            publishers = game.publishers?.map { it.name },
            developers = game.developers?.map { it.name },
            genres = game.genres?.map { it.name },
            themes = game.themes?.map { it.name },
            keywords = game.keywords?.toList(),
            features = game.features?.map { it.name },
            perspectives = game.perspectives?.map { it.name },
            imageIds = game.images?.mapNotNull { it.id },
            videoUrls = game.videoUrls?.map { it.toString() },
            path = game.path,
            metadata = toDto(game.metadata)
        )
    }

    private fun toDto(metadata: Map<String, FieldMetadata>): Map<String, GameMetadataDto> {
        return metadata.mapValues { toDto(it.value) }
    }

    private fun toDto(metadata: FieldMetadata): GameMetadataDto {
        return GameMetadataDto(
            source = metadata.source.pluginId,
            lastUpdated = metadata.lastUpdated
        )
    }

    private fun toEntity(companyName: String, companyType: CompanyType): Company {
        companyRepository.findByNameAndType(companyName, companyType)?.let { return it }
        val company = Company(name = companyName, type = companyType)
        return companyRepository.save(company)
    }

    private fun downloadAndPersist(imageUrl: URI, type: ImageType): Image {
        val parsedUrl = imageUrl.toURL()
        imageRepository.findByOriginalUrl(parsedUrl)?.let { return it }

        val image = Image(originalUrl = parsedUrl, type = type)
        parsedUrl.openStream().use { input ->
            image.mimeType = URLConnection.guessContentTypeFromName(parsedUrl.file)
            imageContentStore.setContent(image, input)
        }
        return imageRepository.save(image)
    }
}