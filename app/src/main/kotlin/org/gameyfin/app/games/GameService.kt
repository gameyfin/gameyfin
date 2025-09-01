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
import org.gameyfin.app.core.plugins.dto.ExternalProviderIdDto
import org.gameyfin.app.core.plugins.management.GameyfinPluginDescriptor
import org.gameyfin.app.core.plugins.management.GameyfinPluginManager
import org.gameyfin.app.core.plugins.management.PluginManagementEntry
import org.gameyfin.app.core.replaceRomanNumerals
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.games.dto.*
import org.gameyfin.app.games.entities.*
import org.gameyfin.app.games.extensions.toDtos
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.media.ImageService
import org.gameyfin.app.users.UserService
import org.gameyfin.pluginapi.gamemetadata.*
import org.springframework.data.repository.findByIdOrNull
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
import java.util.concurrent.Executors
import java.util.concurrent.Future
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
        private val gameUserEvents = Sinks.many().multicast().onBackpressureBuffer<GameUserEvent>(1024, false)
        private val gameAdminEvents = Sinks.many().multicast().onBackpressureBuffer<GameAdminEvent>(1024, false)

        fun subscribeUser(): Flux<List<GameUserEvent>> {
            log.debug { "New user subscription for gameUpdates" }
            return gameUserEvents.asFlux()
                .buffer(100.milliseconds.toJavaDuration())
                .doOnSubscribe {
                    log.debug { "Subscriber added to gameUserEvents [${gameUserEvents.currentSubscriberCount()}]" }
                }
                .doFinally {
                    log.debug { "Subscriber removed from gameUserEvents with signal type $it [${gameUserEvents.currentSubscriberCount()}]" }
                }
        }

        fun subscribeAdmin(): Flux<List<GameAdminEvent>> {
            log.debug { "New admin subscription for gameUpdates" }
            return gameAdminEvents.asFlux()
                .buffer(100.milliseconds.toJavaDuration())
                .doOnSubscribe {
                    log.debug { "Subscriber added to gameAdminEvents [${gameAdminEvents.currentSubscriberCount()}]" }
                }
                .doFinally {
                    log.debug { "Subscriber removed from gameAdminEvents with signal type $it [${gameAdminEvents.currentSubscriberCount()}]" }
                }
        }

        fun emitUser(event: GameUserEvent) {
            gameUserEvents.tryEmitNext(event)
        }

        fun emitAdmin(event: GameAdminEvent) {
            gameAdminEvents.tryEmitNext(event)
        }

        private val executor = Executors.newVirtualThreadPerTaskExecutor()
    }

    private val metadataPlugins: List<GameMetadataProvider>
        get() = pluginManager.getExtensions(GameMetadataProvider::class.java)


    fun getAll(): List<GameDto> {
        val entities = gameRepository.findAll()
        return entities.toDtos()
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
            log.error { "Error downloading images for game '${game.title}' (${game.id}): ${e.message}" }
            log.debug(e) {}
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

    fun edit(gameUpdateDto: GameUpdateDto) {
        val existingGame = gameRepository.findByIdOrNull(gameUpdateDto.id)
            ?: throw IllegalArgumentException("Game with ID $gameUpdateDto.id not found")

        val user = when (val userDetails = getCurrentAuth().principal) {
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

    @Transactional
    fun update(game: Game): Game? {
        var wasGameUpdated = false

        val game = getById(game.id!!)

        val originalIds: Map<String, ExternalProviderIdDto> = game.metadata.originalIds
            .map { (provider, originalId) ->
                val providerId = pluginManager.getExtensions(provider.pluginId).first()?.javaClass?.name ?: return null
                val pluginId = provider.pluginId
                val originalId = originalId
                providerId to ExternalProviderIdDto(pluginId, originalId)
            }
            .toMap()

        val updatedGame = matchManually(
            originalIds = originalIds,
            path = Path.of(game.metadata.path),
            library = game.library,
            replaceGameId = game.id,
            persist = false
        )

        if (updatedGame == null) {
            log.warn { "Failed to update game with ID ${game.id}" }
            return null
        }


        fun <T> updateField(
            fieldName: String,
            originalValue: T?,
            updatedValue: T?,
            setValue: (T?) -> Unit,
            updatedFieldMetadata: GameFieldMetadata?
        ) {
            // Hibernate collections are of type "PersistentBag" which does not implement equals() properly when comparing with ArrayList
            fun areEqual(a: Any?, b: Any?): Boolean {
                return when {
                    a is Collection<*> && b is Collection<*> -> a.toList() == b.toList()
                    else -> a == b
                }
            }

            val fieldSource = game.metadata.fields[fieldName]?.source
            if (updatedValue != null && fieldSource !is GameFieldUserSource && !areEqual(
                    originalValue,
                    updatedValue
                ) && updatedFieldMetadata != null
            ) {
                setValue(updatedValue)
                game.metadata.fields[fieldName] = updatedFieldMetadata
                wasGameUpdated = true
            }
        }

        // Title
        updateField(
            "title",
            game.title,
            updatedGame.title,
            { game.title = it },
            updatedGame.metadata.fields["title"]
        )

        // Summary
        updateField(
            "summary",
            game.summary,
            updatedGame.summary,
            { game.summary = it },
            updatedGame.metadata.fields["summary"]
        )

        // Release
        updateField(
            "release",
            game.release,
            updatedGame.release,
            { game.release = it },
            updatedGame.metadata.fields["release"]
        )

        // User Rating
        updateField(
            "userRating",
            game.userRating,
            updatedGame.userRating,
            { game.userRating = it },
            updatedGame.metadata.fields["userRating"]
        )

        // Critic Rating
        updateField(
            "criticRating",
            game.criticRating,
            updatedGame.criticRating,
            { game.criticRating = it },
            updatedGame.metadata.fields["criticRating"]
        )

        // Cover Image
        updateField(
            "coverImage",
            game.coverImage,
            updatedGame.coverImage,
            { game.coverImage = it },
            updatedGame.metadata.fields["coverImage"]
        )

        // Header Image
        updateField(
            "headerImage",
            game.headerImage,
            updatedGame.headerImage,
            { game.headerImage = it },
            updatedGame.metadata.fields["headerImage"]
        )

        // Publishers
        updateField(
            "publishers",
            game.publishers,
            updatedGame.publishers,
            { game.publishers = it ?: emptyList() },
            updatedGame.metadata.fields["publishers"]
        )

        // Developers
        updateField(
            "developers",
            game.developers,
            updatedGame.developers,
            { game.developers = it ?: emptyList() },
            updatedGame.metadata.fields["developers"]
        )

        // Genres
        updateField(
            "genres",
            game.genres,
            updatedGame.genres,
            { game.genres = it ?: emptyList() },
            updatedGame.metadata.fields["genres"]
        )

        // Themes
        updateField(
            "themes",
            game.themes,
            updatedGame.themes,
            { game.themes = it ?: emptyList() },
            updatedGame.metadata.fields["themes"]
        )

        // Keywords
        updateField(
            "keywords",
            game.keywords,
            updatedGame.keywords,
            { game.keywords = it ?: emptyList() },
            updatedGame.metadata.fields["keywords"]
        )

        // Features
        updateField(
            "features",
            game.features,
            updatedGame.features,
            { game.features = it ?: emptyList() },
            updatedGame.metadata.fields["features"]
        )

        // Perspectives
        updateField(
            "perspectives",
            game.perspectives,
            updatedGame.perspectives,
            { game.perspectives = it ?: emptyList() },
            updatedGame.metadata.fields["perspectives"]
        )

        // Images
        updateField(
            "images",
            game.images,
            updatedGame.images,
            { game.images = it ?: emptyList() },
            updatedGame.metadata.fields["images"]
        )

        // Video URLs
        updateField(
            "videoUrls",
            game.videoUrls,
            updatedGame.videoUrls,
            { game.videoUrls = it ?: emptyList() },
            updatedGame.metadata.fields["videoUrls"]
        )

        return if (wasGameUpdated) game else null
    }

    fun delete(gameId: Long) {
        gameRepository.deleteById(gameId)
    }

    fun getPotentialMatches(searchTerm: String): List<GameSearchResultDto> {
        // 1. Query all plugins for up to 10 results each
        val futures: List<Future<List<Pair<GameMetadataProvider, PluginApiMetadata>>>> = metadataPlugins.map { plugin ->
            executor.submit<List<Pair<GameMetadataProvider, PluginApiMetadata>>> {
                try {
                    plugin.fetchByTitle(searchTerm, 10).map { plugin to it }
                } catch (e: Exception) {
                    val pluginWrapper = pluginManager.getPluginForExtension(plugin.javaClass)
                    log.warn { "Error fetching metadata for searchterm '$searchTerm' with plugin '${(pluginWrapper?.descriptor as GameyfinPluginDescriptor?)?.pluginName ?: pluginWrapper?.pluginId ?: plugin.javaClass.name}': ${e.message}" }
                    log.debug(e) {}
                    emptyList()
                }
            }
        }
        val results = futures.flatMap { it.get() }

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
            val originalIds: Map<String, ExternalProviderIdDto> = group
                .mapNotNull { (provider, metadata) ->
                    val providerId = provider.javaClass.name
                    val pluginId = providerToManagementEntry[provider]?.pluginId ?: return@mapNotNull null
                    val originalId = metadata.originalId
                    if (providerId != null) providerId to ExternalProviderIdDto(pluginId, originalId) else null
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
        originalIds: Map<String, ExternalProviderIdDto>,
        path: Path,
        library: Library,
        replaceGameId: Long? = null,
        persist: Boolean = true
    ): Game? {
        // Step 0: Query all metadata plugins for metadata on the provided originalIds
        val metadataResults = runBlocking {
            coroutineScope {
                metadataPlugins.associateWith { plugin ->
                    async {
                        val originalId = originalIds[plugin.javaClass.name]?.externalProviderId ?: return@async null
                        try {
                            return@async plugin.fetchById(originalId)
                        } catch (e: Exception) {
                            val pluginWrapper = pluginManager.getPluginForExtension(plugin.javaClass)
                            log.warn { "Error fetching metadata for game [id: $originalId] with plugin '${(pluginWrapper?.descriptor as GameyfinPluginDescriptor?)?.pluginName ?: pluginWrapper?.pluginId ?: plugin.javaClass.name}': ${e.message}" }
                            log.debug(e) {}
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
        return if (persist) create(mergedGame) else mergedGame
    }

    fun matchFromFile(path: Path, library: Library): Game? {
        var query = FilenameUtils.removeExtension(path.fileName.toString())

        // (Optional) Step -1: Extract title from filename using regex
        if (config.get(ConfigProperties.Libraries.Scan.ExtractTitleUsingRegex) == true) {
            val regexString = config.get(ConfigProperties.Libraries.Scan.TitleExtractionRegex)
            if (regexString != null && regexString.isNotEmpty()) {
                try {
                    val regex = Regex(regexString)
                    val originalQuery = query
                    query = regex.find(query)?.value?.trim() ?: query.also {
                        log.warn { "No match found for regex '$regexString' in filename '$query'. Using full filename." }
                    }
                    log.debug { "Extracted title '$query' from filename '$originalQuery'" }
                } catch (_: Exception) {
                    log.error { "Title extraction regex ($regexString) is invalid, using fill filename." }
                }
            } else {
                log.warn { "No regex configured for title extraction, using full filename '$query'" }
            }
        }

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
        val futures = metadataPlugins.associateWith { plugin ->
            executor.submit<PluginApiMetadata?> {
                try {
                    plugin.fetchByTitle(gameTitle).firstOrNull()
                } catch (e: Exception) {
                    val pluginWrapper = pluginManager.getPluginForExtension(plugin.javaClass)
                    log.warn { "Error fetching metadata for game title '$gameTitle' with plugin '${(pluginWrapper?.descriptor as GameyfinPluginDescriptor?)?.pluginName ?: pluginWrapper?.pluginId ?: plugin.javaClass.name}': ${e.message}" }
                    log.debug(e) {}
                    null
                }
            }
        }
        return futures.mapValues { it.value.get() }
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