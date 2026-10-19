package org.gameyfin.app.core.plugins.marketplace

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.core.plugins.PluginService
import org.gameyfin.app.core.plugins.dto.PluginDto
import org.gameyfin.app.core.plugins.management.GameyfinPluginManager
import org.gameyfin.app.core.plugins.management.PluginManagementRepository
import org.gameyfin.app.core.plugins.marketplace.dto.*
import org.pf4j.update.PluginInfo.PluginRelease
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.core.annotation.Order
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.net.URI
import java.time.Duration
import java.util.*

@Service
class PluginMarketplaceService(
    private val updateManager: GameyfinUpdateManager,
    private val pluginManager: GameyfinPluginManager,
    private val pluginService: PluginService,
    private val marketplaceRepository: PluginMarketplaceRepository,
    private val pluginManagementRepository: PluginManagementRepository,
    private val httpClient: MarketplaceHttpClient,
    @Value("\${gameyfin.plugins.marketplace.official-url:$DEFAULT_OFFICIAL_MARKETPLACE_URL}")
    private val officialMarketplaceUrl: String = DEFAULT_OFFICIAL_MARKETPLACE_URL,
) {
    companion object {
        private val log = KotlinLogging.logger {}

        const val OFFICIAL_MARKETPLACE_ID = "official"
        const val OFFICIAL_MARKETPLACE_NAME = "Gameyfin Official"
        const val DEFAULT_OFFICIAL_MARKETPLACE_URL = "https://github.com/gameyfin/plugin-marketplace"
        private const val MAX_NAME_LENGTH = 255

        private val SUPPORTED_LOGO_TYPES = setOf("image/png", "image/jpeg", "image/gif", "image/webp", "image/svg+xml")
    }

    private val logoCache: Cache<String, Optional<ByteArray>> = Caffeine.newBuilder()
        .maximumSize(500)
        .expireAfterWrite(Duration.ofHours(6))
        .build()

    @Order(0)
    @EventListener(ApplicationReadyEvent::class)
    fun initialize() {
        try {
            seedOfficialMarketplace()
        } catch (e: Exception) {
            log.error(e) { "Failed to initialize the official plugin marketplace" }
        }
        syncRepositories()
    }

    /* Marketplaces */

    fun getMarketplaces(): List<PluginMarketplaceDto> {
        return sortedMarketplaces().map { toDto(it) }
    }

    fun addMarketplace(create: PluginMarketplaceCreateDto): PluginMarketplaceDto {
        val name = validateName(create.name)
        val indexUrl = resolveIndexUrl(create.url)

        require(!marketplaceRepository.existsByIndexUrl(indexUrl.toString())) {
            "A marketplace with this URL already exists"
        }

        val id = UUID.randomUUID().toString()

        // Make sure the index can be loaded before saving the marketplace
        val plugins = updateManager.createRepository(id, indexUrl).fetchOrThrow()
        log.info { "Adding plugin marketplace '$name' ($indexUrl) with ${plugins.size} plugins" }

        val entry = marketplaceRepository.save(
            PluginMarketplaceEntry(id = id, name = name, url = create.url.trim(), indexUrl = indexUrl.toString())
        )
        syncRepositories()
        return toDto(entry)
    }

    fun updateMarketplace(id: String, update: PluginMarketplaceUpdateDto): PluginMarketplaceDto {
        val entry = marketplaceRepository.findByIdOrNull(id)
            ?: throw IllegalArgumentException("Marketplace '$id' not found")

        update.name?.let {
            require(!entry.official) { "The official marketplace can't be renamed" }
            entry.name = validateName(it)
        }
        update.enabled?.let { entry.enabled = it }

        marketplaceRepository.save(entry)
        syncRepositories()
        return toDto(entry)
    }

    fun removeMarketplace(id: String) {
        val entry = marketplaceRepository.findByIdOrNull(id)
            ?: throw IllegalArgumentException("Marketplace '$id' not found")
        require(!entry.official) { "The official marketplace can't be removed" }

        log.info { "Removing plugin marketplace '${entry.name}' (${entry.indexUrl})" }
        marketplaceRepository.delete(entry)
        syncRepositories()
    }

    /**
     * Forces a re-fetch of all marketplace indexes.
     */
    fun refresh(): List<MarketplacePluginDto> {
        updateManager.refresh()
        logoCache.invalidateAll()
        return getPlugins()
    }

    /* Plugins */

    fun getPlugins(): List<MarketplacePluginDto> {
        val marketplaces = marketplaceRepository.findAll().associateBy { it.id }

        return updateManager.getMarketplaceRepositories().flatMap { repository ->
            val marketplace = marketplaces[repository.id] ?: return@flatMap emptyList()
            repository.getGameyfinPlugins().values.map { toDto(it, marketplace) }
        }
    }

    fun install(marketplaceId: String, pluginId: String, version: String?): PluginDto? {
        try {
            updateManager.installPlugin(marketplaceId, pluginId, version)
        } finally {
            pluginService.emitPlugin(pluginId)
        }
        return pluginService.getDto(pluginId)
    }

    fun update(pluginId: String, version: String?): PluginDto? {
        require(pluginManager.getPlugin(pluginId) != null) { "Plugin '$pluginId' is not installed" }
        val pluginInfo = updateManager.findPluginInfo(pluginId)
            ?: throw IllegalArgumentException("Plugin '$pluginId' was not found in any marketplace")
        return install(pluginInfo.repositoryId, pluginId, version)
    }

    fun uninstall(pluginId: String): PluginDto? {
        try {
            updateManager.uninstall(pluginId)
        } finally {
            pluginService.emitPlugin(pluginId)
        }
        return pluginService.getDto(pluginId)
    }

    /**
     * Returns the logo of a marketplace plugin (fetched via the backend to avoid leaking client information
     * to 3rd-party hosts and to enforce size limits).
     */
    fun getLogo(marketplaceId: String, pluginId: String): ByteArray? {
        val repository = updateManager.getMarketplaceRepository(marketplaceId) ?: return null
        val logoUrl = repository.getGameyfinPlugins()[pluginId]?.logoUrl ?: return null

        return logoCache.get(logoUrl) {
            try {
                Optional.of(httpClient.getBytes(URI(logoUrl), MarketplaceHttpClient.MAX_LOGO_SIZE))
            } catch (e: Exception) {
                log.debug { "Failed to fetch logo of plugin '$pluginId' from '$logoUrl': ${e.message}" }
                Optional.empty()
            }
        }.orElse(null)
    }

    fun isSupportedLogoType(mediaType: String?): Boolean = mediaType?.lowercase() in SUPPORTED_LOGO_TYPES

    /* Internals */

    private fun seedOfficialMarketplace() {
        val indexUrl = resolveIndexUrl(officialMarketplaceUrl).toString()
        val existing = marketplaceRepository.findByIdOrNull(OFFICIAL_MARKETPLACE_ID)

        if (existing == null) {
            log.info { "Adding official plugin marketplace ($indexUrl)" }
            marketplaceRepository.save(
                PluginMarketplaceEntry(
                    id = OFFICIAL_MARKETPLACE_ID,
                    name = OFFICIAL_MARKETPLACE_NAME,
                    url = officialMarketplaceUrl,
                    indexUrl = indexUrl,
                    official = true
                )
            )
        } else if (existing.url != officialMarketplaceUrl || existing.indexUrl != indexUrl) {
            log.info { "Updating URL of official plugin marketplace to $indexUrl" }
            existing.url = officialMarketplaceUrl
            existing.indexUrl = indexUrl
            marketplaceRepository.save(existing)
        }
    }

    /**
     * Synchronizes the repositories of the update manager with the database (official marketplace first).
     */
    @Synchronized
    fun syncRepositories() {
        val existing = updateManager.getMarketplaceRepositories().associateBy { it.id }

        val repositories = sortedMarketplaces()
            .filter { it.enabled }
            .mapNotNull { entry ->
                val indexUrl = try {
                    URI(entry.indexUrl)
                } catch (e: Exception) {
                    log.warn { "Ignoring marketplace '${entry.name}' due to invalid URL '${entry.indexUrl}'" }
                    return@mapNotNull null
                }
                existing[entry.id]?.takeIf { it.indexUrl == indexUrl }
                    ?: updateManager.createRepository(entry.id, indexUrl)
            }

        updateManager.setMarketplaceRepositories(repositories)
    }

    private fun sortedMarketplaces(): List<PluginMarketplaceEntry> {
        return marketplaceRepository.findAll()
            .sortedWith(compareByDescending<PluginMarketplaceEntry> { it.official }.thenBy { it.createdAt })
    }

    private fun resolveIndexUrl(url: String): URI {
        return MarketplaceUrlResolver.resolveIndexUrl(url, httpClient.allowInsecureUrls)
    }

    private fun validateName(name: String): String {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Name must not be empty" }
        require(trimmed.length <= MAX_NAME_LENGTH) { "Name must not be longer than $MAX_NAME_LENGTH characters" }
        return trimmed
    }

    private fun toDto(entry: PluginMarketplaceEntry): PluginMarketplaceDto {
        val repository = updateManager.getMarketplaceRepository(entry.id)
        return PluginMarketplaceDto(
            id = entry.id,
            name = entry.name,
            url = entry.url,
            indexUrl = entry.indexUrl,
            enabled = entry.enabled,
            official = entry.official,
            pluginCount = if (entry.enabled) repository?.getGameyfinPlugins()?.size ?: 0 else 0,
            lastFetchedAt = repository?.lastFetchedAt,
            lastError = repository?.lastError
        )
    }

    private fun toDto(info: GameyfinPluginInfo, marketplace: PluginMarketplaceEntry): MarketplacePluginDto {
        val releases = info.releases.orEmpty()
            .sortedWith { a, b -> compareVersions(b.version, a.version) }

        val latest = releases.firstOrNull()
        val latestCompatible = releases.firstOrNull { updateManager.isCompatible(it) }

        val installed = pluginManager.getPlugin(info.id)
        val installedVersion = installed?.descriptor?.version

        val status = when {
            installed == null && latestCompatible == null -> MarketplacePluginStatus.INCOMPATIBLE
            installed == null -> MarketplacePluginStatus.NOT_INSTALLED
            latestCompatible != null && compareVersions(latestCompatible.version, installedVersion) > 0 ->
                MarketplacePluginStatus.UPDATE_AVAILABLE

            else -> MarketplacePluginStatus.INSTALLED
        }

        return MarketplacePluginDto(
            id = info.id,
            marketplaceId = marketplace.id,
            marketplaceName = marketplace.name,
            officialMarketplace = marketplace.official,
            name = info.name ?: info.id,
            shortDescription = info.shortDescription,
            description = info.description,
            author = info.provider,
            license = info.license,
            url = info.projectUrl,
            hasLogo = info.logoUrl != null,
            types = info.types,
            tags = info.tags,
            releases = releases.map { toDto(it) },
            latestVersion = latest?.version,
            latestCompatibleVersion = latestCompatible?.version,
            installedVersion = installedVersion,
            installedOrigin = installed?.let { pluginManager.getPluginOrigin(info.id) },
            installedFromMarketplaceId = installed?.let {
                pluginManagementRepository.findByIdOrNull(info.id)?.sourceMarketplaceId
            },
            status = status
        )
    }

    private fun toDto(release: PluginRelease): MarketplacePluginReleaseDto {
        return MarketplacePluginReleaseDto(
            version = release.version,
            date = release.date?.takeIf { it.time > 0 }?.toInstant(),
            requires = release.requires?.takeIf { it.isNotBlank() && it != "*" },
            compatible = updateManager.isCompatible(release),
            changelog = (release as? GameyfinPluginRelease)?.changelog
        )
    }

    private fun compareVersions(v1: String?, v2: String?): Int {
        if (v1 == null || v2 == null) return compareValues(v1, v2)
        return try {
            pluginManager.versionManager.compareVersions(v1, v2)
        } catch (_: Exception) {
            v1.compareTo(v2)
        }
    }
}
