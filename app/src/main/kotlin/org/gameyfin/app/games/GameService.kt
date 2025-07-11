package org.gameyfin.app.games

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.apache.commons.io.FilenameUtils
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.core.alphaNumeric
import org.gameyfin.app.core.filesystem.FilesystemService
import org.gameyfin.app.core.filterValuesNotNull
import org.gameyfin.app.core.plugins.PluginService
import org.gameyfin.app.core.plugins.management.GameyfinPluginManager
import org.gameyfin.app.core.plugins.management.PluginManagementEntry
import org.gameyfin.app.core.replaceRomanNumerals
import org.gameyfin.app.games.dto.*
import org.gameyfin.app.games.entities.*
import org.gameyfin.app.games.entities.GameMetadata
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.libraries.Library
import org.gameyfin.app.media.ImageService
import org.gameyfin.app.users.UserService
import org.gameyfin.pluginapi.gamemetadata.*
import org.springframework.data.repository.findByIdOrNull
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import java.net.URI
import java.nio.file.Path
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration
import org.gameyfin.pluginapi.gamemetadata.GameMetadata as PluginApiMetadata

@Service
class GameService(
    private val gameRepository: GameRepository,
    private val pluginManager: GameyfinPluginManager,
    private val pluginService: PluginService,
    private val config: ConfigService,
    private val companyService: CompanyService,
    private val userService: UserService,
    private val imageService: ImageService,
    private val filesystemService: FilesystemService
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
    fun create(game: Game): Game? {
        game.publishers = game.publishers.map { companyService.createOrGet(it) }
        game.developers = game.developers.map { companyService.createOrGet(it) }

        try {
            game.coverImage?.let {
                imageService.downloadIfNew(it)
            }

            game.headerImage?.let {
                imageService.downloadIfNew(it)
            }

            game.images.map {
                imageService.downloadIfNew(it)
            }
        } catch (e: Exception) {
            log.error(e) { "Error downloading images for game: ${e.message}" }
            null
        }

        game.metadata.fileSize = filesystemService.calculateFileSize(game.metadata.path)

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

    fun update(gameUpdateDto: GameUpdateDto) {
        val existingGame = gameRepository.findByIdOrNull(gameUpdateDto.id)
            ?: throw IllegalArgumentException("Game with ID $gameUpdateDto.id not found")

        val userDetails = SecurityContextHolder.getContext().authentication.principal
        val user = when (userDetails) {
            is UserDetails -> userService.getByUsernameNonNull(userDetails.username)
            is OidcUser -> userService.getByUsernameNonNull(userDetails.preferredUsername)
            else -> throw IllegalStateException("Unkown user type: ${userDetails::class.java.name}")
        }

        // Update only non-null fields
        gameUpdateDto.title?.let {
            existingGame.title = it
            existingGame.metadata.fields["title"]?.source = GameFieldUserSource(user = user)
        }
        gameUpdateDto.release?.let {
            existingGame.release = it.atStartOfDay(ZoneOffset.UTC).toInstant()
            existingGame.metadata.fields["release"]?.source = GameFieldUserSource(user = user)
        }
        gameUpdateDto.coverUrl?.let {
            val newCoverImage = Image(originalUrl = URI.create(it).toURL(), type = ImageType.COVER)
            imageService.downloadIfNew(newCoverImage)

            existingGame.coverImage = newCoverImage
            existingGame.metadata.fields["coverImage"]?.source = GameFieldUserSource(user = user)
        }
        gameUpdateDto.headerUrl?.let {
            val newHeaderImage = Image(originalUrl = URI.create(it).toURL(), type = ImageType.HEADER)
            imageService.downloadIfNew(newHeaderImage)

            existingGame.headerImage = newHeaderImage
            existingGame.metadata.fields["headerImage"]?.source = GameFieldUserSource(user = user)
        }
        gameUpdateDto.comment?.let {
            existingGame.comment = it
            existingGame.metadata.fields["comment"]?.source = GameFieldUserSource(user = user)
        }
        gameUpdateDto.summary?.let {
            existingGame.summary = it
            existingGame.metadata.fields["summary"]?.source = GameFieldUserSource(user = user)
        }
        gameUpdateDto.developers?.let {
            existingGame.developers =
                it.map { name -> companyService.createOrGet(Company(name = name, type = CompanyType.DEVELOPER)) }
            existingGame.metadata.fields["developers"]?.source = GameFieldUserSource(user = user)
        }
        gameUpdateDto.publishers?.let {
            existingGame.publishers =
                it.map { name -> companyService.createOrGet(Company(name = name, type = CompanyType.PUBLISHER)) }
            existingGame.metadata.fields["publishers"]?.source = GameFieldUserSource(user = user)
        }
        gameUpdateDto.genres?.let {
            existingGame.genres = it.mapNotNull { name ->
                try {
                    Genre.valueOf(name)
                } catch (_: IllegalArgumentException) {
                    log.error { "Invalid value for genre '$name', must be one of ${Genre.entries}" }
                    null
                }
            }
            existingGame.metadata.fields["genres"]?.source = GameFieldUserSource(user = user)
        }
        gameUpdateDto.themes?.let {
            existingGame.themes = it.mapNotNull { name ->
                try {
                    Theme.valueOf(name)
                } catch (_: IllegalArgumentException) {
                    log.error { "Invalid value for theme '$name', must be one of ${Theme.entries}" }
                    null
                }
            }
            existingGame.metadata.fields["themes"]?.source = GameFieldUserSource(user = user)
        }
        gameUpdateDto.keywords?.let {
            existingGame.keywords = it
            existingGame.metadata.fields["keywords"]?.source = GameFieldUserSource(user = user)
        }
        gameUpdateDto.features?.let {
            existingGame.features = it.mapNotNull { name ->
                try {
                    GameFeature.valueOf(name)
                } catch (_: IllegalArgumentException) {
                    log.error { "Invalid value for feature '$name', must be one of ${GameFeature.entries}" }
                    null
                }
            }
            existingGame.metadata.fields["features"]?.source = GameFieldUserSource(user = user)
        }
        gameUpdateDto.perspectives?.let {
            existingGame.perspectives = it.mapNotNull { name ->
                try {
                    PlayerPerspective.valueOf(name)
                } catch (_: IllegalArgumentException) {
                    log.error { "Invalid value for perspective '$name', must be one of ${PlayerPerspective.entries}" }
                    null
                }
            }
            existingGame.metadata.fields["perspectives"]?.source = GameFieldUserSource(user = user)
        }


        gameUpdateDto.metadata?.let { metadata ->
            metadata.matchConfirmed?.let { existingGame.metadata.matchConfirmed = it }
        }

        gameRepository.save(existingGame)
    }

    fun delete(gameId: Long) {
        gameRepository.deleteById(gameId)
    }

    fun getPotentialMatches(searchTerm: String): List<GameSearchResultDto> {
        // 1. Query all plugins for up to 10 results each
        val results = metadataPlugins.flatMap { plugin ->
            try {
                plugin.fetchByTitle(searchTerm, 10)
                    .map { plugin to it }
            } catch (e: Exception) {
                log.error(e) { "Error fetching metadata for game with plugin ${plugin.javaClass.name}" }
                emptyList()
            }
        }
        val providerToManagementEntry =
            results.toMap().entries.associate { it.key to pluginService.getPluginManagementEntry(it.key.javaClass) }

        // 2. Group by title and release year (if available)
        // (NOTE: This _could_ lead to problems if multiple games have the (almost) same title - see Battlefront 2)
        data class GroupKey(val title: String, val year: Int?)

        fun PluginApiMetadata.groupKey(): GroupKey =
            GroupKey(
                title = this.title.normalizeGameTitle(),
                year = this.release?.atZone(ZoneId.systemDefault())?.year
            )

        val grouped = results.groupBy { (_, metadata) -> metadata.groupKey() }

        // 3. Merge each group into one GameSearchResultDto using plugin priorities

        fun pluginPriority(plugin: GameMetadataProvider) = providerToManagementEntry[plugin]?.priority ?: 0

        fun mergeGroup(group: List<Pair<GameMetadataProvider, PluginApiMetadata>>): GameSearchResultDto {
            val sorted = group.sortedByDescending { (provider, _) -> pluginPriority(provider) }

            fun <T> pick(selector: (PluginApiMetadata) -> T?): T? = sorted.firstNotNullOfOrNull { selector(it.second) }
            fun <T> pickList(selector: (PluginApiMetadata) -> List<T>?): List<T>? =
                sorted.mapNotNull { selector(it.second) }.firstOrNull { it.isNotEmpty() }

            // Collect originalIds for this group
            val originalIds: Map<String, OriginalIdDto> = group
                .mapNotNull { (provider, metadata) ->
                    val providerId = provider.javaClass.name
                    val pluginId = providerToManagementEntry[provider]?.pluginId ?: return@mapNotNull null
                    val originalId = metadata.originalId
                    if (providerId != null) providerId to OriginalIdDto(pluginId, originalId) else null
                }
                .toMap()

            // Merge and deduplicate coverUrls and headerUrls
            val coverUrls = group.flatMap {
                it.second.coverUrls?.mapNotNull { url ->
                    val pluginId = providerToManagementEntry[it.first]?.pluginId ?: return@mapNotNull null
                    UrlWithSourceDto(url = url.toString(), pluginId = pluginId)
                } ?: emptyList()
            }.distinct()

            val headerUrls = group.flatMap {
                it.second.headerUrls?.mapNotNull { url ->
                    val pluginId = providerToManagementEntry[it.first]?.pluginId ?: return@mapNotNull null
                    UrlWithSourceDto(url = url.toString(), pluginId = pluginId)
                } ?: emptyList()
            }.distinct()

            return GameSearchResultDto(
                title = pick { it.title }!!,
                coverUrls = coverUrls.ifEmpty { null },
                headerUrls = headerUrls.ifEmpty { null },
                release = pick { it.release },
                publishers = pickList { it.publishedBy?.toList() },
                developers = pickList { it.developedBy?.toList() },
                originalIds = originalIds
            )
        }

        // 4. Merge the results
        val mergedResults = grouped.values.map { mergeGroup(it) }

        // 5. Sort the results by fuzzy match ratio and then by release year (newer first)
        val sortedResults = mergedResults
            .map { result ->
                val ratio = FuzzySearch.ratio(searchTerm.normalizeGameTitle(), result.title.normalizeGameTitle())
                result to ratio
            }
            .sortedWith(
                compareByDescending<Pair<GameSearchResultDto, Int>> { it.second }
                    .thenComparator { a, b ->
                        val yearA = a.first.release?.atZone(ZoneId.systemDefault())?.year
                        val yearB = b.first.release?.atZone(ZoneId.systemDefault())?.year
                        when {
                            yearA == yearB -> 0
                            yearA == null -> 1 // nulls last
                            yearB == null -> -1
                            else -> yearB.compareTo(yearA) // newer first
                        }
                    }
            )
            .map { it.first }

        return sortedResults
    }

    fun matchManually(
        originalIds: Map<String, OriginalIdDto>,
        path: Path,
        library: Library,
        replaceGameId: Long? = null
    ): Game? {
        // Step 0: Query all metadata plugins for metadata on the provided originalIds
        val metadataResults = runBlocking {
            coroutineScope {
                metadataPlugins.associateWith { plugin ->
                    async {
                        val originalId = originalIds[plugin.javaClass.name]?.originalId ?: return@async null
                        try {
                            return@async plugin.fetchById(originalId)
                        } catch (e: Exception) {
                            log.error(e) { "Error fetching metadata for game [id: $originalId] with plugin ${plugin.javaClass.name}" }
                            null
                        }
                    }.await()
                }
            }
        }

        // Step 1: Filter out invalid (empty) results
        // In theory all results should be valid
        val validResults = metadataResults.filterValuesNotNull()
        if (validResults.isEmpty()) {
            log.error { "No results found for originalIds: $originalIds" }
            return null
        }

        // Step 3: Merge results into a single Game entity
        val mergedGame = mergeResults(validResults, path, library)

        // Step 4: If a replaceGameId is provided, set it (overwriting the existing entity)
        if (replaceGameId != null) {
            val existingGame = getById(replaceGameId)

            // Copy fields from the existing game to the merged game
            mergedGame.id = existingGame.id
            mergedGame.createdAt = existingGame.createdAt
            mergedGame.metadata.downloadCount = existingGame.metadata.downloadCount
        }

        mergedGame.metadata.matchConfirmed = true

        // Step 6: Save the game
        return create(mergedGame)
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

    fun getById(id: Long): Game {
        return gameRepository.findByIdOrNull(id) ?: throw IllegalArgumentException("Game with id $id not found")
    }

    fun incrementDownloadCount(game: Game) {
        game.metadata.downloadCount++
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
                            it.fetchByTitle(gameTitle).firstOrNull()
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
                metadata.coverUrls?.firstOrNull()?.let { coverUrl ->
                    if (!metadataMap.containsKey("coverImage")) {
                        mergedGame.coverImage = Image(originalUrl = coverUrl.toURL(), type = ImageType.COVER)
                        metadataMap["coverImage"] =
                            GameFieldMetadata(source = GameFieldPluginSource(plugin = sourcePlugin))
                    }
                }
                metadata.headerUrls?.firstOrNull()?.let { headerUrl ->
                    if (!metadataMap.containsKey("headerImage")) {
                        mergedGame.headerImage = Image(originalUrl = headerUrl.toURL(), type = ImageType.HEADER)
                        metadataMap["headerImage"] =
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
                    source = source.user.username,
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
        headerId = this.headerImage?.id,
        comment = this.comment,
        summary = this.summary,
        release = this.release?.atZone(ZoneOffset.UTC)?.toLocalDate(),
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