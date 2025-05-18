package de.grimsi.gameyfin.games

import de.grimsi.gameyfin.core.alphaNumeric
import de.grimsi.gameyfin.core.filterValuesNotNull
import de.grimsi.gameyfin.core.plugins.management.PluginManagementEntry
import de.grimsi.gameyfin.core.plugins.management.PluginManagementService
import de.grimsi.gameyfin.core.replaceRomanNumerals
import de.grimsi.gameyfin.games.dto.GameDto
import de.grimsi.gameyfin.games.dto.GameMetadataDto
import de.grimsi.gameyfin.games.entities.*
import de.grimsi.gameyfin.games.repositories.GameRepository
import de.grimsi.gameyfin.libraries.Library
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadata
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadataProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.apache.commons.io.FilenameUtils
import org.pf4j.PluginManager
import org.springframework.data.domain.Limit
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.file.Path

@Service
class GameService(
    private val pluginManager: PluginManager,
    private val pluginManagementService: PluginManagementService,
    private val gameRepository: GameRepository,
    private val companyService: CompanyService
) {
    companion object {
        const val TITLE_MATCH_MIN_RATIO = 90

        private val log = KotlinLogging.logger {}
    }

    private val metadataPlugins: List<GameMetadataProvider>
        get() = pluginManager.getExtensions(GameMetadataProvider::class.java)

    fun createOrUpdate(game: Game): Game {
        gameRepository.findByPath(game.path)?.let { game.id = it.id }
        return gameRepository.save(game)
    }

    @Transactional
    fun create(games: List<Game>): List<Game> {
        val gamesToBePersisted = games.filter { it.id == null }

        gamesToBePersisted.forEach { game ->
            game.publishers = game.publishers.map { companyService.createOrGet(it) }
            game.developers = game.developers.map { companyService.createOrGet(it) }
            game
        }

        return gameRepository.saveAll(gamesToBePersisted)
    }

    fun getGame(id: Long): GameDto {
        return gameRepository.findByIdOrNull(id)?.toDto()
            ?: throw IllegalArgumentException("Game with id $id not found")
    }

    fun matchFromFile(path: Path, library: Library): Game? {
        val query = FilenameUtils.removeExtension(path.fileName.toString())

        // Step 0: Query all metadata plugins for metadata on the provided game title
        val metadataResults = queryPlugins(query)

        // Step 1: Filter out invalid (empty) results
        val validResults = metadataResults.filterValuesNotNull()
        if (validResults.isEmpty()) {
            log.error { "Could not identify game at path '$path'" }
            return null
        }

        // Step 2: Filter results to find the best matching title
        val filteredResults = filterResults(query, validResults)

        // Step 3: Merge results into a single Game entity
        val mergedGame = mergeResults(filteredResults, path, library)

        // Step 4: Save the new game
        return mergedGame
    }

    fun getAllByPaths(paths: List<String>): List<Game> {
        return gameRepository.findAllByPathIn(paths)
    }

    fun getAllGames(): List<GameDto> {
        val entities = gameRepository.findAll()
        return entities.map { it.toDto() }
    }

    fun delete(game: Game) {
        gameRepository.delete(game)
    }

    fun deleteAll() {
        gameRepository.deleteAll()
    }

    fun getMostRecentlyAdded(count: Int): List<GameDto> {
        return gameRepository.findByOrderByCreatedAtDesc(Limit.of(count))
            .map { it.toDto() }
    }

    fun getMostRecentlyUpdated(count: Int): List<GameDto> {
        return gameRepository.findByOrderByCreatedAtDesc(Limit.of(count))
            .map { it.toDto() }
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
                            it.fetchMetadata(gameTitle).firstOrNull()
                        } catch (e: Exception) {
                            log.error(e) { "Error fetching metadata for game with plugin ${it.javaClass.name}" }
                            null
                        }
                    }.await()
                }
            }
        }
    }

    /**
     * Determines the closest matching title from the results
     * Filters the results to only include the best matching title (using an alphanumeric comparison)
     */
    private fun filterResults(
        originalQuery: String,
        results: Map<GameMetadataProvider, GameMetadata>
    ): Map<GameMetadataProvider, GameMetadata> {
        val providerToTitle = results.entries.associate {
            pluginManager.whichPlugin(it.key.javaClass).pluginId to it.value.title
        }

        val bestMatchingTitle = FuzzySearch.extractOne(originalQuery, providerToTitle.values).string

        log.debug {
            "Best matching title: '$bestMatchingTitle' (${
                providerToTitle.count { it.value.fuzzyMatchTitle(bestMatchingTitle) }
            }/${providerToTitle.size} matches) for '$originalQuery' determined from $providerToTitle"
        }

        return results.filter { it.value.title.fuzzyMatchTitle(bestMatchingTitle) }
    }

    /**
     * Merges the results from the metadata plugins into a single Game entity
     * The merging is done by taking the first non-null value for each field
     * The plugin with the highest possible priority is used as the source for each field
     */
    private fun mergeResults(
        results: Map<GameMetadataProvider, GameMetadata?>,
        path: Path,
        library: Library
    ): Game {
        val mergedGame = Game(path = path.toString(), library = library)
        val metadataMap = mutableMapOf<String, FieldMetadata>()
        val originalIdsMap = mutableMapOf<PluginManagementEntry, String>()

        // Cache the plugin management entries for each provider
        val providerToManagementEntry =
            results.entries.associate { it.key to pluginManagementService.getPluginManagementEntry(it.key.javaClass) }

        // Sort results by plugin priority
        val sortedResults = results.entries.sortedByDescending {
            pluginManagementService.getPluginManagementEntry(it.key.javaClass).priority
        }

        sortedResults.forEach { (provider, metadata) ->
            val sourcePlugin = providerToManagementEntry[provider] ?: return@forEach

            metadata?.let { metadata ->
                originalIdsMap[sourcePlugin] = metadata.originalId

                metadata.title.takeIf { it.isNotBlank() }?.let { title ->
                    if (!metadataMap.containsKey("title")) {
                        mergedGame.title = title
                        metadataMap["title"] = FieldMetadata(sourcePlugin)
                    }
                }
                metadata.description?.takeIf { it.isNotBlank() }?.let { description ->
                    if (!metadataMap.containsKey("summary")) {
                        mergedGame.summary = description
                        metadataMap["summary"] = FieldMetadata(sourcePlugin)
                    }
                }
                metadata.coverUrl?.let { coverUrl ->
                    if (!metadataMap.containsKey("coverImage")) {
                        mergedGame.coverImage = Image(originalUrl = coverUrl.toURL(), type = ImageType.COVER)
                        metadataMap["coverImage"] = FieldMetadata(sourcePlugin)
                    }
                }
                metadata.release?.let { release ->
                    if (!metadataMap.containsKey("release")) {
                        mergedGame.release = release
                        metadataMap["release"] = FieldMetadata(sourcePlugin)
                    }
                }
                metadata.userRating?.let { userRating ->
                    if (!metadataMap.containsKey("userRating")) {
                        mergedGame.userRating = userRating
                        metadataMap["userRating"] = FieldMetadata(sourcePlugin)
                    }
                }
                metadata.criticRating?.let { criticRating ->
                    if (!metadataMap.containsKey("criticRating")) {
                        mergedGame.criticRating = criticRating
                        metadataMap["criticRating"] = FieldMetadata(sourcePlugin)
                    }
                }
                metadata.publishedBy?.takeIf { it.isNotEmpty() }?.let { publishedBy ->
                    if (!metadataMap.containsKey("publishers")) {
                        mergedGame.publishers =
                            publishedBy.map { Company(name = it, type = CompanyType.PUBLISHER) }
                        metadataMap["publishers"] = FieldMetadata(sourcePlugin)
                    }
                }
                metadata.developedBy?.takeIf { it.isNotEmpty() }?.let { developedBy ->
                    if (!metadataMap.containsKey("developers")) {
                        mergedGame.developers =
                            developedBy.map { Company(name = it, type = CompanyType.DEVELOPER) }
                        metadataMap["developers"] = FieldMetadata(sourcePlugin)
                    }
                }
                metadata.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                    if (!metadataMap.containsKey("genres")) {
                        mergedGame.genres = genres.toList()
                        metadataMap["genres"] = FieldMetadata(sourcePlugin)
                    }
                }
                metadata.themes?.takeIf { it.isNotEmpty() }?.let { themes ->
                    if (!metadataMap.containsKey("themes")) {
                        mergedGame.themes = themes.toList()
                        metadataMap["themes"] = FieldMetadata(sourcePlugin)
                    }
                }
                metadata.keywords?.takeIf { it.isNotEmpty() }?.let { keywords ->
                    if (!metadataMap.containsKey("keywords")) {
                        mergedGame.keywords = keywords.toList()
                        metadataMap["keywords"] = FieldMetadata(sourcePlugin)
                    }
                }
                metadata.features?.takeIf { it.isNotEmpty() }?.let { features ->
                    if (!metadataMap.containsKey("features")) {
                        mergedGame.features = features.toList()
                        metadataMap["features"] = FieldMetadata(sourcePlugin)
                    }
                }
                metadata.perspectives?.takeIf { it.isNotEmpty() }?.let { perspectives ->
                    if (!metadataMap.containsKey("perspectives")) {
                        mergedGame.perspectives = perspectives.toList()
                        metadataMap["perspectives"] = FieldMetadata(sourcePlugin)
                    }
                }
                metadata.screenshotUrls?.takeIf { it.isNotEmpty() }?.let { screenshotUrls ->
                    if (!metadataMap.containsKey("images")) {
                        mergedGame.images = runBlocking {
                            screenshotUrls.map { Image(originalUrl = it.toURL(), type = ImageType.SCREENSHOT) }
                        }
                        metadataMap["images"] = FieldMetadata(sourcePlugin)
                    }
                }
                metadata.videoUrls?.takeIf { it.isNotEmpty() }?.let { videoUrls ->
                    if (!metadataMap.containsKey("videoUrls")) {
                        mergedGame.videoUrls = videoUrls.toList()
                        metadataMap["videoUrls"] = FieldMetadata(sourcePlugin)
                    }
                }
            }
        }

        mergedGame.metadata = metadataMap
        mergedGame.originalIds = originalIdsMap

        return mergedGame
    }

    private fun String.fuzzyMatchTitle(other: String, minRatio: Int = TITLE_MATCH_MIN_RATIO): Boolean {
        return FuzzySearch.ratio(this.normalizeGameTitle(), other.normalizeGameTitle()) > minRatio
    }

    fun String.normalizeGameTitle(): String = this.alphaNumeric().replaceRomanNumerals()
}


fun Game.toDto(): GameDto {
    // Helper functions
    fun toDto(metadata: FieldMetadata): GameMetadataDto {
        return GameMetadataDto(
            source = metadata.source.pluginId,
            lastUpdated = metadata.lastUpdated
        )
    }

    fun toDto(metadata: Map<String, FieldMetadata>): Map<String, GameMetadataDto> {
        return metadata.mapValues { toDto(it.value) }
    }


    val thisId = this.id ?: throw IllegalArgumentException("this ID is null")
    val createdAt = this.createdAt ?: throw IllegalArgumentException("this creation timestamp is null")
    val updatedAt = this.updatedAt ?: throw IllegalArgumentException("this update timestamp is null")
    val thisLibraryId = this.library.id ?: throw IllegalArgumentException("this library ID is null")
    val thisTitle = this.title ?: throw IllegalArgumentException("this title is null")

    return GameDto(
        id = thisId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        libraryId = thisLibraryId,
        title = thisTitle,
        coverId = this.coverImage?.id,
        comment = this.comment,
        summary = this.summary,
        release = this.release,
        userRating = this.userRating,
        criticRating = this.criticRating,
        publishers = this.publishers.map { it.name },
        developers = this.developers.map { it.name },
        genres = this.genres.map { it.name },
        themes = this.themes.map { it.name },
        keywords = this.keywords.toList(),
        features = this.features.map { it.name },
        perspectives = this.perspectives?.map { it.name },
        imageIds = this.images.mapNotNull { it.id },
        videoUrls = this.videoUrls.map { it.toString() },
        path = this.path,
        fileSize = this.fileSize ?: 0L,
        metadata = toDto(this.metadata),
        originalIds = this.originalIds.mapKeys { it.key.pluginId }
    )
}