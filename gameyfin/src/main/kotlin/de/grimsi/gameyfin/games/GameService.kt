package de.grimsi.gameyfin.games

import de.grimsi.gameyfin.config.ConfigProperties
import de.grimsi.gameyfin.config.ConfigService
import de.grimsi.gameyfin.core.alphaNumeric
import de.grimsi.gameyfin.core.filterValuesNotNull
import de.grimsi.gameyfin.core.plugins.PluginService
import de.grimsi.gameyfin.core.plugins.management.PluginManagementEntry
import de.grimsi.gameyfin.core.replaceRomanNumerals
import de.grimsi.gameyfin.games.dto.*
import de.grimsi.gameyfin.games.entities.*
import de.grimsi.gameyfin.games.repositories.GameRepository
import de.grimsi.gameyfin.libraries.Library
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadataProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.apache.commons.io.FilenameUtils
import org.pf4j.PluginManager
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.nio.file.Path
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration
import de.grimsi.gameyfin.pluginapi.gamemetadata.GameMetadata as PluginApiMetadata

@Service
class GameService(
    private val pluginManager: PluginManager,
    private val pluginService: PluginService,
    private val config: ConfigService,
    private val companyService: CompanyService,
    private val gameRepository: GameRepository
) {
    companion object {
        private val log = KotlinLogging.logger {}

        /* Websockets */
        private val gameEvents = Sinks.many().multicast().onBackpressureBuffer<GameEvent>(1024, false)

        fun subscribe(): Flux<List<GameEvent>> {
            log.debug { "New subscription for gameUpdates" }
            return gameEvents.asFlux()
                .buffer(100.milliseconds.toJavaDuration())
                .doOnSubscribe {
                    log.debug { "Subscriber added to gameEvents [${gameEvents.currentSubscriberCount()}]" }
                }
                .doFinally {
                    log.debug { "Subscriber removed from gameEvents with signal type $it [${gameEvents.currentSubscriberCount()}]" }
                }
        }

        fun emit(event: GameEvent) {
            gameEvents.tryEmitNext(event)
        }
    }

    private val metadataPlugins: List<GameMetadataProvider>
        get() = pluginManager.getExtensions(GameMetadataProvider::class.java)


    fun getAll(): List<GameDto> {
        val entities = gameRepository.findAll()
        return entities.map { it.toDto() }
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

    fun update(gameUpdateDto: GameUpdateDto) {
        val existingGame = gameRepository.findByIdOrNull(gameUpdateDto.id)
            ?: throw IllegalArgumentException("Game with ID $gameUpdateDto.id not found")

        // Update only non-null fields
        gameUpdateDto.title?.let { existingGame.title = it }
        gameUpdateDto.comment?.let { existingGame.comment = it }
        gameUpdateDto.summary?.let { existingGame.summary = it }
        gameUpdateDto.metadata?.let { metadata ->
            metadata.matchConfirmed?.let { existingGame.metadata.matchConfirmed = it }
        }

        gameRepository.save(existingGame)
    }

    fun delete(gameId: Long) {
        gameRepository.deleteById(gameId)
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
        return gameRepository.findAllByMetadata_PathIn(paths)
    }

    fun getById(id: Long): Game {
        return gameRepository.findByIdOrNull(id) ?: throw IllegalArgumentException("Game with id $id not found")
    }

    fun setMatchConfirmed(gameId: Long, confirmed: Boolean) {
        val game = getById(gameId)
        game.metadata.matchConfirmed = confirmed
        gameRepository.save(game)
    }

    /**
     * Queries all metadata plugins for metadata on the provided game title
     * Runs the queries concurrently and asynchronously
     * @return A map of metadata plugins and their respective results
     */
    private fun queryPlugins(gameTitle: String): Map<GameMetadataProvider, PluginApiMetadata?> {
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
        results: Map<GameMetadataProvider, PluginApiMetadata>
    ): Map<GameMetadataProvider, PluginApiMetadata> {
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
        results: Map<GameMetadataProvider, PluginApiMetadata?>,
        path: Path,
        library: Library
    ): Game {
        val mergedGame = Game(path = path, library = library)
        val metadataMap = mutableMapOf<String, GameFieldMetadata>()
        val originalIdsMap = mutableMapOf<PluginManagementEntry, String>()

        // Cache the plugin management entries for each provider
        val providerToManagementEntry =
            results.entries.associate { it.key to pluginService.getPluginManagementEntry(it.key.javaClass) }

        // Sort results by plugin priority
        val sortedResults = results.entries.sortedByDescending {
            providerToManagementEntry[it.key]?.priority
        }

        sortedResults.forEach { (provider, metadata) ->
            val sourcePlugin = providerToManagementEntry[provider] ?: return@forEach

            metadata?.let { metadata ->
                originalIdsMap[sourcePlugin] = metadata.originalId

                metadata.title.takeIf { it.isNotBlank() }?.let { title ->
                    if (!metadataMap.containsKey("title")) {
                        mergedGame.title = title
                        metadataMap["title"] = GameFieldMetadata(source = GameFieldPluginSource(plugin = sourcePlugin))
                    }
                }
                metadata.description?.takeIf { it.isNotBlank() }?.let { description ->
                    if (!metadataMap.containsKey("summary")) {
                        mergedGame.summary = description
                        metadataMap["summary"] =
                            GameFieldMetadata(source = GameFieldPluginSource(plugin = sourcePlugin))
                    }
                }
                metadata.coverUrl?.let { coverUrl ->
                    if (!metadataMap.containsKey("coverImage")) {
                        mergedGame.coverImage = Image(originalUrl = coverUrl.toURL(), type = ImageType.COVER)
                        metadataMap["coverImage"] =
                            GameFieldMetadata(source = GameFieldPluginSource(plugin = sourcePlugin))
                    }
                }
                metadata.release?.let { release ->
                    if (!metadataMap.containsKey("release")) {
                        mergedGame.release = release
                        metadataMap["release"] =
                            GameFieldMetadata(source = GameFieldPluginSource(plugin = sourcePlugin))
                    }
                }
                metadata.userRating?.let { userRating ->
                    if (!metadataMap.containsKey("userRating")) {
                        mergedGame.userRating = userRating
                        metadataMap["userRating"] =
                            GameFieldMetadata(source = GameFieldPluginSource(plugin = sourcePlugin))
                    }
                }
                metadata.criticRating?.let { criticRating ->
                    if (!metadataMap.containsKey("criticRating")) {
                        mergedGame.criticRating = criticRating
                        metadataMap["criticRating"] =
                            GameFieldMetadata(source = GameFieldPluginSource(plugin = sourcePlugin))
                    }
                }
                metadata.publishedBy?.takeIf { it.isNotEmpty() }?.let { publishedBy ->
                    if (!metadataMap.containsKey("publishers")) {
                        mergedGame.publishers =
                            publishedBy.map { Company(name = it, type = CompanyType.PUBLISHER) }
                        metadataMap["publishers"] =
                            GameFieldMetadata(source = GameFieldPluginSource(plugin = sourcePlugin))
                    }
                }
                metadata.developedBy?.takeIf { it.isNotEmpty() }?.let { developedBy ->
                    if (!metadataMap.containsKey("developers")) {
                        mergedGame.developers =
                            developedBy.map { Company(name = it, type = CompanyType.DEVELOPER) }
                        metadataMap["developers"] =
                            GameFieldMetadata(source = GameFieldPluginSource(plugin = sourcePlugin))
                    }
                }
                metadata.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                    if (!metadataMap.containsKey("genres")) {
                        mergedGame.genres = genres.toList()
                        metadataMap["genres"] = GameFieldMetadata(source = GameFieldPluginSource(plugin = sourcePlugin))
                    }
                }
                metadata.themes?.takeIf { it.isNotEmpty() }?.let { themes ->
                    if (!metadataMap.containsKey("themes")) {
                        mergedGame.themes = themes.toList()
                        metadataMap["themes"] = GameFieldMetadata(source = GameFieldPluginSource(plugin = sourcePlugin))
                    }
                }
                metadata.keywords?.takeIf { it.isNotEmpty() }?.let { keywords ->
                    if (!metadataMap.containsKey("keywords")) {
                        mergedGame.keywords = keywords.toList()
                        metadataMap["keywords"] =
                            GameFieldMetadata(source = GameFieldPluginSource(plugin = sourcePlugin))
                    }
                }
                metadata.features?.takeIf { it.isNotEmpty() }?.let { features ->
                    if (!metadataMap.containsKey("features")) {
                        mergedGame.features = features.toList()
                        metadataMap["features"] =
                            GameFieldMetadata(source = GameFieldPluginSource(plugin = sourcePlugin))
                    }
                }
                metadata.perspectives?.takeIf { it.isNotEmpty() }?.let { perspectives ->
                    if (!metadataMap.containsKey("perspectives")) {
                        mergedGame.perspectives = perspectives.toList()
                        metadataMap["perspectives"] =
                            GameFieldMetadata(source = GameFieldPluginSource(plugin = sourcePlugin))
                    }
                }
                metadata.screenshotUrls?.takeIf { it.isNotEmpty() }?.let { screenshotUrls ->
                    if (!metadataMap.containsKey("images")) {
                        mergedGame.images = runBlocking {
                            screenshotUrls.map { Image(originalUrl = it.toURL(), type = ImageType.SCREENSHOT) }
                        }
                        metadataMap["images"] = GameFieldMetadata(source = GameFieldPluginSource(plugin = sourcePlugin))
                    }
                }
                metadata.videoUrls?.takeIf { it.isNotEmpty() }?.let { videoUrls ->
                    if (!metadataMap.containsKey("videoUrls")) {
                        mergedGame.videoUrls = videoUrls.toList()
                        metadataMap["videoUrls"] =
                            GameFieldMetadata(source = GameFieldPluginSource(plugin = sourcePlugin))
                    }
                }
            }
        }

        mergedGame.metadata.fields = metadataMap
        mergedGame.metadata.originalIds = originalIdsMap

        return mergedGame
    }

    private fun String.fuzzyMatchTitle(other: String): Boolean {
        val minRatio = config.get(ConfigProperties.Libraries.Scan.TitleMatchMinRatio)!!
        return FuzzySearch.ratio(this.normalizeGameTitle(), other.normalizeGameTitle()) > minRatio
    }

    fun String.normalizeGameTitle(): String = this.alphaNumeric().replaceRomanNumerals()
}


fun Game.toDto(): GameDto {
    // Helper functions
    fun toDto(fieldMetadata: GameFieldMetadata): GameFieldMetadataDto {
        val source = fieldMetadata.source

        return when (source) {
            is GameFieldPluginSource -> {
                GameFieldMetadataDto(
                    type = GameFieldMetadataType.PLUGIN,
                    source = source.plugin.pluginId,
                    updatedAt = fieldMetadata.updatedAt!!
                )
            }

            is GameFieldUserSource -> {
                GameFieldMetadataDto(
                    type = GameFieldMetadataType.USER,
                    source = source.user.id!!,
                    updatedAt = fieldMetadata.updatedAt!!
                )
            }

            else -> {
                GameFieldMetadataDto(
                    type = GameFieldMetadataType.UNKNOWN,
                    source = "unknown source",
                    updatedAt = fieldMetadata.updatedAt!!
                )
            }
        }
    }

    fun toDto(metadata: GameMetadata): GameMetadataDto {
        return GameMetadataDto(
            fileSize = metadata.fileSize ?: 0L,
            downloadCount = metadata.downloadCount,
            path = metadata.path,
            fields = metadata.fields.mapValues { toDto(it.value) },
            originalIds = metadata.originalIds.mapKeys { it.key.pluginId },
            matchConfirmed = metadata.matchConfirmed
        )
    }

    return GameDto(
        id = id!!,
        createdAt = createdAt!!,
        updatedAt = updatedAt!!,
        libraryId = this.library.id!!,
        title = title!!,
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
        metadata = toDto(this.metadata)
    )
}