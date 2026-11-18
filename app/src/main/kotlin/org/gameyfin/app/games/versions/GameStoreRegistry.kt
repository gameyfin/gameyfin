package org.gameyfin.app.games.versions

import com.vaadin.hilla.Endpoint
import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.plugins.PluginService
import org.gameyfin.app.core.plugins.management.GameyfinPluginManager
import org.gameyfin.pluginapi.gamestore.GameStoreInfo
import org.gameyfin.pluginapi.gamestore.GameStoreDlc
import org.gameyfin.pluginapi.gamestore.GameStoreProduct
import org.gameyfin.pluginapi.gamestore.GameStoreProvider
import org.gameyfin.pluginapi.gamestore.GameStoreVersion
import org.springframework.stereotype.Service

private val log = KotlinLogging.logger {}

/** A game store offered by an active plugin. */
data class GameStoreDto(
    val id: String,
    val displayName: String,
    /** ID of the plugin that provides the store */
    val pluginId: String,
    /** Allows the UI to validate input and to extract product IDs from pasted URLs */
    val productIdPattern: String,
    val urlPatterns: List<String>,
    val caseInsensitiveIds: Boolean,
    /** False if no active plugin of the store can search, products then have to be added by ID or URL */
    val supportsSearch: Boolean
)

/**
 * A game store offered by one or more active plugins. [provider] combines all sources of the store, see
 * [CompositeGameStoreProvider].
 */
class ActiveGameStore(
    val info: GameStoreInfo,
    val provider: GameStoreProvider,
    /** The plugin with the highest priority that provides the store */
    val pluginId: String,
    val priority: Int
) {
    val id get() = info.id

    fun toDto() = GameStoreDto(
        info.id, info.displayName, pluginId, info.productIdPattern, info.urlPatterns, info.caseInsensitiveIds,
        provider.supportsSearch
    )

    /** Accepts a plain product ID or a URL of a well-known site and returns the normalized product ID. */
    fun parseProductId(input: String): String? = info.parseProductId(input)

    fun isValidProductId(id: String): Boolean = Regex(info.productIdPattern).matches(id)
}

/**
 * Combines several data sources of the same store, ordered by plugin priority (highest first).
 *
 * - Search and versions: the first source with a result wins, the others are fallbacks (also if a source fails).
 *   This never yields duplicates. If no source has a result and one failed, the failure is rethrown.
 * - DLC and edition names: results of all sources are merged, the highest priority source wins per entry.
 */
class CompositeGameStoreProvider(
    override val store: GameStoreInfo,
    private val sources: List<GameStoreProvider>
) : GameStoreProvider {

    override val supportsSearch: Boolean get() = sources.any { it.supportsSearch }

    override fun search(query: String): List<GameStoreProduct> = firstNonEmpty { it.search(query) }

    override fun fetchVersions(productId: String): List<GameStoreVersion> = firstNonEmpty { it.fetchVersions(productId) }

    override fun fetchDlc(productId: String): List<GameStoreDlc> =
        merge { it.fetchDlc(productId) }.distinctBy { it.id }

    override fun suggestEditions(productId: String): List<String> =
        merge { it.suggestEditions(productId) }.distinctBy { it.lowercase() }

    private fun <T> firstNonEmpty(call: (GameStoreProvider) -> List<T>): List<T> {
        var failure: Exception? = null
        for (source in sources) {
            try {
                val result = call(source)
                if (result.isNotEmpty()) return result
            } catch (e: Exception) {
                log.warn(e) { "Game store source ${source.javaClass.name} failed, trying the next one" }
                failure = failure ?: e
            }
        }
        failure?.let { throw it }
        return emptyList()
    }

    private fun <T> merge(call: (GameStoreProvider) -> List<T>): List<T> {
        var failure: Exception? = null
        var succeeded = false
        val merged = sources.flatMap { source ->
            try {
                call(source).also { succeeded = true }
            } catch (e: Exception) {
                log.warn(e) { "Game store source ${source.javaClass.name} failed" }
                failure = failure ?: e
                emptyList()
            }
        }
        if (!succeeded) failure?.let { throw it }
        return merged
    }
}

/**
 * Looks up the game stores that are provided by plugins ([GameStoreProvider]).
 *
 * Only providers of plugins that are started count. Several active plugins can provide the same store (one per
 * data source), they are combined by a [CompositeGameStoreProvider] in order of plugin priority.
 * Version tracking requires at least one active store, see [isAvailable].
 */
@Service
class GameStoreRegistry(
    private val pluginManager: GameyfinPluginManager,
    private val pluginService: PluginService
) {

    fun activeStores(): List<ActiveGameStore> {
        val stores = pluginManager.getExtensions(GameStoreProvider::class.java).mapNotNull { provider ->
            try {
                val entry = pluginService.getPluginManagementEntry(provider.javaClass)
                ActiveGameStore(provider.store, provider, entry.pluginId, entry.priority)
            } catch (e: Exception) {
                log.warn(e) { "Ignoring game store provider ${provider.javaClass.name}" }
                null
            }
        }

        return combineStoreSources(stores)
    }

    /** Whether at least one plugin provides a game store. */
    fun isAvailable(): Boolean = activeStores().isNotEmpty()

    fun find(storeId: String): ActiveGameStore? = activeStores().firstOrNull { it.id == storeId }

    /** @throws StoreUnavailableException if no active plugin provides the store */
    fun require(storeId: String): ActiveGameStore = find(storeId) ?: throw StoreUnavailableException(storeId)

    /**
     * Validates a product ID or URL entered for a store.
     * @throws StoreUnavailableException if no active plugin provides the store
     * @throws IllegalArgumentException if the input is not valid for the store
     */
    fun parseProductId(storeId: String, input: String): Pair<ActiveGameStore, String> {
        val store = require(storeId)
        val id = store.parseProductId(input)
            ?: throw IllegalArgumentException("Invalid ${store.info.displayName} product ID or URL: '$input'")
        return store to id
    }
}

/**
 * Groups the providers by store and combines each group into one store. Stores are ordered like the plugins in the
 * plugin administration (highest priority first), so admins decide which store is listed first.
 */
internal fun combineStoreSources(sources: List<ActiveGameStore>): List<ActiveGameStore> =
    sources.groupBy { it.id }.values.map { candidates ->
        val ordered = candidates.sortedWith(compareByDescending<ActiveGameStore> { it.priority }.thenBy { it.pluginId })
        val primary = ordered.first()
        ActiveGameStore(
            primary.info,
            if (ordered.size == 1) primary.provider
            else CompositeGameStoreProvider(primary.info, ordered.map { it.provider }),
            primary.pluginId,
            primary.priority
        )
    }.sortedWith(compareByDescending<ActiveGameStore> { it.priority }.thenBy { it.info.displayName.lowercase() })

/** Thrown when a store is used whose plugin is not installed or not enabled. */
class StoreUnavailableException(val storeId: String) :
    IllegalStateException("The plugin for the game store '$storeId' is not installed or not enabled")

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class GameStoreEndpoint(private val registry: GameStoreRegistry) {

    /** The stores of all active plugins. Empty if version tracking is unavailable. */
    fun getStores(): List<GameStoreDto> = registry.activeStores().map { it.toDto() }
}
