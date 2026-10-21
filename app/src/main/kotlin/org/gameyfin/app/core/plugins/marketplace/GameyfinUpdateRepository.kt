package org.gameyfin.app.core.plugins.marketplace

import io.github.oshai.kotlinlogging.KotlinLogging
import org.pf4j.update.FileDownloader
import org.pf4j.update.FileVerifier
import org.pf4j.update.PluginInfo
import org.pf4j.update.UpdateRepository
import java.net.URI
import java.net.URL
import java.time.Duration
import java.time.Instant

/**
 * PF4J [UpdateRepository] backed by a marketplace `plugins.json` index.
 * The index is fetched lazily and cached for [cacheTtl]. If fetching fails, the last known index is kept.
 */
class GameyfinUpdateRepository(
    private val id: String,
    val indexUrl: URI,
    private val httpClient: MarketplaceHttpClient,
    private val fileDownloader: FileDownloader,
    private val fileVerifier: FileVerifier,
    private val cacheTtl: Duration = Duration.ofHours(1),
    private val errorRetryInterval: Duration = Duration.ofMinutes(1),
) : UpdateRepository {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    @Volatile
    private var plugins: Map<String, GameyfinPluginInfo>? = null

    @Volatile
    private var lastAttemptAt: Instant? = null

    @Volatile
    var lastFetchedAt: Instant? = null
        private set

    @Volatile
    var lastError: String? = null
        private set

    override fun getId(): String = id

    override fun getUrl(): URL = indexUrl.toURL()

    override fun getPlugins(): Map<String, PluginInfo> = getGameyfinPlugins()

    override fun getPlugin(id: String): PluginInfo? = getGameyfinPlugins()[id]

    /**
     * Forces a re-fetch of the index on next access.
     */
    override fun refresh() {
        lastAttemptAt = null
    }

    override fun getFileDownloader(): FileDownloader = fileDownloader

    override fun getFileVerifier(): FileVerifier = fileVerifier

    @Synchronized
    fun getGameyfinPlugins(): Map<String, GameyfinPluginInfo> {
        val cached = plugins
        val lastAttempt = lastAttemptAt
        val now = Instant.now()

        val maxAge = if (lastError == null) cacheTtl else errorRetryInterval
        if (lastAttempt != null && lastAttempt.plus(maxAge).isAfter(now)) {
            return cached.orEmpty()
        }

        lastAttemptAt = now
        return try {
            val json = httpClient.getString(indexUrl, MarketplaceHttpClient.MAX_INDEX_SIZE)
            val parsed = MarketplaceIndexParser.parse(json, indexUrl, id)
            plugins = parsed
            lastFetchedAt = now
            lastError = null
            log.debug { "Fetched ${parsed.size} plugins from marketplace '$id' ($indexUrl)" }
            parsed
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
            log.warn { "Failed to fetch plugin index of marketplace '$id' from '$indexUrl': $lastError" }
            cached.orEmpty()
        }
    }

    /**
     * Fetches the index immediately and throws if this fails.
     * Used to validate a marketplace before it is added.
     */
    fun fetchOrThrow(): Map<String, GameyfinPluginInfo> {
        refresh()
        val result = getGameyfinPlugins()
        lastError?.let { throw IllegalArgumentException("Could not load marketplace index from '$indexUrl': $it") }
        return result
    }
}
