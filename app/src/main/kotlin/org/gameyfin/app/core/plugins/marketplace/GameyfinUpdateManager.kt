package org.gameyfin.app.core.plugins.marketplace

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.core.plugins.management.GameyfinPluginManager
import org.gameyfin.app.core.plugins.management.PluginTrustLevel
import org.gameyfin.app.core.plugins.management.PluginManagementRepository
import org.pf4j.PluginRuntimeException
import org.pf4j.PluginState
import org.pf4j.update.FileVerifier
import org.pf4j.update.PluginInfo
import org.pf4j.update.PluginInfo.PluginRelease
import org.pf4j.update.UpdateManager
import org.pf4j.update.UpdateRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * PF4J [UpdateManager] for the Gameyfin plugin marketplace.
 *
 * Differences to the default implementation:
 * - Plugins are installed into a dedicated directory ([GameyfinPluginManager.installedPluginsRoot]),
 *   bundled/manually added plugins are never modified or deleted
 * - Compatibility checks use the same logic as the plugin manager (see [GameyfinPluginManager.isCompatible])
 * - Downloads are verified (mandatory SHA-512 checksum, plugin ID/version/compatibility from the JAR manifest)
 * - A failed update is rolled back to the previously loaded version
 * - Repositories are queried in order, so the official marketplace takes precedence over 3rd-party ones
 */
@Component
class GameyfinUpdateManager(
    private val pluginManager: GameyfinPluginManager,
    private val httpClient: MarketplaceHttpClient,
    private val pluginManagementRepository: PluginManagementRepository,
    @Value("\${gameyfin.plugins.marketplace.cache-ttl:1h}")
    private val cacheTtl: Duration = Duration.ofHours(1),
) : UpdateManager(pluginManager, null as Path?) {

    companion object {
        private val log = KotlinLogging.logger {}
        private const val DOWNLOAD_DIRECTORY = ".download"
        private val UNSAFE_FILE_NAME_CHARS = Regex("[^A-Za-z0-9._-]")
    }

    private val lock = ReentrantLock()
    private val marketplaceRepositories = CopyOnWriteArrayList<GameyfinUpdateRepository>()

    private val downloadDirectory: Path
        get() = pluginManager.installedPluginsRoot.resolve(DOWNLOAD_DIRECTORY)

    private val fileDownloader = GameyfinFileDownloader(httpClient) { downloadDirectory }
    private val fileVerifier: FileVerifier = MarketplaceFileVerifier(httpClient, pluginManager)

    init {
        // Not used since all accessors are overridden, but initialized to avoid NPEs in inherited methods
        repositories = mutableListOf()
        cleanUpDownloadDirectory()
    }

    fun createRepository(id: String, indexUrl: URI): GameyfinUpdateRepository {
        return GameyfinUpdateRepository(id, indexUrl, httpClient, fileDownloader, fileVerifier, cacheTtl)
    }

    /**
     * Replaces all marketplace repositories. The order defines the precedence if a plugin is listed in multiple
     * marketplaces.
     */
    fun setMarketplaceRepositories(newRepositories: List<GameyfinUpdateRepository>) {
        lock.withLock {
            marketplaceRepositories.clear()
            marketplaceRepositories.addAll(newRepositories)
        }
    }

    fun getMarketplaceRepositories(): List<GameyfinUpdateRepository> = marketplaceRepositories.toList()

    fun getMarketplaceRepository(id: String): GameyfinUpdateRepository? = marketplaceRepositories.find { it.id == id }

    override fun getRepositories(): List<UpdateRepository> = marketplaceRepositories.toList()

    override fun setRepositories(repositories: List<UpdateRepository>) {
        setMarketplaceRepositories(repositories.filterIsInstance<GameyfinUpdateRepository>())
        refresh()
    }

    override fun addRepository(newRepo: UpdateRepository) {
        require(newRepo is GameyfinUpdateRepository) { "Only marketplace repositories are supported" }
        require(getMarketplaceRepository(newRepo.id) == null) { "Repository with id ${newRepo.id} already exists" }
        marketplaceRepositories.add(newRepo)
    }

    override fun addRepository(id: String, url: java.net.URL) {
        addRepository(createRepository(id, url.toURI()))
    }

    override fun removeRepository(id: String) {
        marketplaceRepositories.removeIf { it.id == id }
    }

    @Synchronized
    override fun refresh() {
        marketplaceRepositories.forEach { it.refresh() }
    }

    /**
     * Plugins of all marketplaces. If a plugin is listed in multiple marketplaces, the first one wins.
     */
    override fun getPluginsMap(): Map<String, PluginInfo> {
        val result = LinkedHashMap<String, PluginInfo>()
        marketplaceRepositories.forEach { repository ->
            repository.getGameyfinPlugins().forEach { (id, info) -> result.putIfAbsent(id, info) }
        }
        return result
    }

    /**
     * Finds the plugin in the marketplaces. The marketplace the plugin was installed from takes precedence.
     */
    fun findPluginInfo(pluginId: String, marketplaceId: String? = null): GameyfinPluginInfo? {
        val preferredMarketplace = marketplaceId
            ?: pluginManagementRepository.findByIdOrNull(pluginId)?.sourceMarketplaceId

        preferredMarketplace
            ?.let { getMarketplaceRepository(it) }
            ?.getGameyfinPlugins()?.get(pluginId)
            ?.let { return it }

        if (marketplaceId != null) return null

        return marketplaceRepositories.firstNotNullOfOrNull { it.getGameyfinPlugins()[pluginId] }
    }

    fun isCompatible(release: PluginRelease): Boolean = pluginManager.isCompatible(release.requires)

    /**
     * Returns the latest release (by version) which is compatible with this Gameyfin instance.
     */
    override fun getLastPluginRelease(id: String): PluginRelease? {
        val pluginInfo = findPluginInfo(id) ?: return null
        return getLastCompatibleRelease(pluginInfo)
    }

    fun getLastCompatibleRelease(pluginInfo: PluginInfo): PluginRelease? {
        return pluginInfo.releases.orEmpty()
            .filter { isCompatible(it) }
            .maxWithOrNull { a, b -> compareVersions(a.version, b.version) }
    }

    fun getLastRelease(pluginInfo: PluginInfo): PluginRelease? {
        return pluginInfo.releases.orEmpty().maxWithOrNull { a, b -> compareVersions(a.version, b.version) }
    }

    override fun hasPluginUpdate(id: String): Boolean {
        val installedVersion = pluginManager.getPlugin(id)?.descriptor?.version ?: return false
        val last = getLastPluginRelease(id) ?: return false
        return compareVersions(last.version, installedVersion) > 0
    }

    override fun installPlugin(id: String, version: String?): Boolean {
        val marketplaceId = findPluginInfo(id)?.repositoryId
            ?: throw PluginRuntimeException("Plugin {} not found in any marketplace", id)
        return installPlugin(marketplaceId, id, version) == PluginState.STARTED
    }

    override fun updatePlugin(id: String, version: String?): Boolean {
        if (pluginManager.getPlugin(id) == null) {
            throw PluginRuntimeException("Plugin {} cannot be updated since it is not installed", id)
        }
        return installPlugin(id, version)
    }

    override fun uninstallPlugin(id: String): Boolean {
        uninstall(id)
        return true
    }

    /**
     * Installs (or updates/downgrades) a plugin from the given marketplace.
     *
     * @param version the version to install or null for the latest compatible version
     * @return the state of the plugin after the installation
     */
    fun installPlugin(marketplaceId: String, pluginId: String, version: String?): PluginState {
        return lock.withLock {
            val repository = getMarketplaceRepository(marketplaceId)
                ?: throw IllegalArgumentException("Marketplace '$marketplaceId' not found")
            val pluginInfo = repository.getGameyfinPlugins()[pluginId]
                ?: throw IllegalArgumentException("Plugin '$pluginId' not found in marketplace '$marketplaceId'")
            val release = findRelease(pluginInfo, version)

            if (!isCompatible(release)) {
                throw IllegalStateException(
                    "Version ${release.version} of plugin '$pluginId' requires Gameyfin '${release.requires}', " +
                            "but this is ${pluginManager.systemVersion}"
                )
            }

            checkInstallable(pluginId, release)

            log.info { "Installing plugin '$pluginId' version ${release.version} from marketplace '$marketplaceId'" }

            val downloaded = download(repository, pluginId, release)
            try {
                replacePlugin(pluginId, release, downloaded, marketplaceId)
            } finally {
                Files.deleteIfExists(downloaded)
            }
        }
    }

    /**
     * Uninstalls a plugin which has been installed via the marketplace.
     * If a local (e.g. bundled) version of the plugin exists, it is loaded instead.
     *
     * @return the state of the local version of the plugin or null if there is none
     */
    fun uninstall(pluginId: String): PluginState? {
        return lock.withLock {
            val pluginWrapper = pluginManager.getPlugin(pluginId)
                ?: throw IllegalArgumentException("Plugin '$pluginId' is not installed")

            if (!pluginManager.isInInstalledPluginsRoot(pluginWrapper.pluginPath)) {
                throw IllegalStateException("Plugin '$pluginId' was not installed via the marketplace and can't be uninstalled")
            }

            log.info { "Uninstalling plugin '$pluginId' (${pluginWrapper.pluginPath})" }

            if (!pluginManager.deletePlugin(pluginId)) {
                throw IllegalStateException(
                    "Failed to uninstall plugin '$pluginId'" +
                            (pluginWrapper.failedException?.message?.let { ": $it" } ?: "")
                )
            }

            pluginManagementRepository.findByIdOrNull(pluginId)?.let {
                it.sourceMarketplaceId = null
                pluginManagementRepository.save(it)
            }

            val localPath = pluginManager.findLocalPluginPath(pluginId) ?: return@withLock null
            log.info { "Falling back to local version of plugin '$pluginId' from '$localPath'" }
            try {
                pluginManager.loadPluginOrThrow(localPath)
                startIfEnabled(pluginId)
            } catch (e: Exception) {
                log.error(e) { "Failed to load local version of plugin '$pluginId' from '$localPath'" }
                null
            }
        }
    }

    private fun findRelease(pluginInfo: GameyfinPluginInfo, version: String?): PluginRelease {
        if (version.isNullOrBlank()) {
            return getLastCompatibleRelease(pluginInfo)
                ?: throw IllegalStateException(
                    "No version of plugin '${pluginInfo.id}' is compatible with Gameyfin ${pluginManager.systemVersion}"
                )
        }

        return pluginInfo.releases.orEmpty().firstOrNull { compareVersions(it.version, version) == 0 }
            ?: throw IllegalArgumentException("Version $version of plugin '${pluginInfo.id}' not found")
    }

    /**
     * Prevents installations which would be pointless or overridden on the next restart.
     */
    private fun checkInstallable(pluginId: String, release: PluginRelease) {
        val installed = pluginManager.getPlugin(pluginId)
        if (installed != null
            && pluginManager.isInInstalledPluginsRoot(installed.pluginPath)
            && compareVersions(installed.descriptor.version, release.version) == 0
        ) {
            throw IllegalStateException("Version ${release.version} of plugin '$pluginId' is already installed")
        }

        val localPath = pluginManager.findLocalPluginPath(pluginId) ?: return
        val localDescriptor = pluginManager.readPluginDescriptor(localPath) ?: return

        if (pluginManager.isCompatible(localDescriptor.requires)
            && compareVersions(localDescriptor.version, release.version) >= 0
        ) {
            throw IllegalStateException(
                "Version ${localDescriptor.version} of plugin '$pluginId' is already provided locally (e.g. bundled " +
                        "with Gameyfin) and would take precedence over version ${release.version}"
            )
        }
    }

    private fun download(repository: UpdateRepository, pluginId: String, release: PluginRelease): Path {
        val downloaded = repository.fileDownloader.downloadFile(URI(release.url).toURL())
        try {
            repository.fileVerifier.verify(FileVerifier.Context(pluginId, release), downloaded)
        } catch (e: Exception) {
            Files.deleteIfExists(downloaded)
            throw e
        }
        return downloaded
    }

    private fun replacePlugin(pluginId: String, release: PluginRelease, downloaded: Path, marketplaceId: String): PluginState {
        val installedRoot = Files.createDirectories(pluginManager.installedPluginsRoot)
        val target = installedRoot.resolve("${sanitize(pluginId)}-${sanitize(release.version)}.jar")

        val previous = pluginManager.getPlugin(pluginId)
        val previousPath = previous?.pluginPath

        if (previous != null) {
            pluginManager.stopPlugin(pluginId)
            if (!pluginManager.unloadPlugin(pluginId)) {
                startIfEnabled(pluginId)
                throw IllegalStateException("Failed to unload the currently installed version of plugin '$pluginId'")
            }
        }

        var loadedId: String? = null
        try {
            move(downloaded, target)
            loadedId = pluginManager.loadPluginOrThrow(target)
            check(loadedId == pluginId) { "Downloaded plugin has ID '$loadedId' instead of '$pluginId'" }
        } catch (e: Exception) {
            log.error { "Failed to install plugin '$pluginId' version ${release.version}: ${e.message}" }
            rollback(pluginId, loadedId, target, previousPath)
            throw e
        }

        if (previousPath != null && previousPath != target && pluginManager.isInInstalledPluginsRoot(previousPath)) {
            deletePath(previousPath)
        }

        pluginManagementRepository.findByIdOrNull(pluginId)?.let {
            // A plugin which is (re-)installed from a different source has to be reviewed and enabled again,
            // unless it is signed by the Gameyfin team
            val sourceChanged = previous == null || it.sourceMarketplaceId != marketplaceId
            if (sourceChanged && it.trustLevel != PluginTrustLevel.OFFICIAL && it.enabled) {
                log.info { "Disabling plugin '$pluginId' since it was installed from a new source and is not signed" }
                pluginManager.disablePlugin(pluginId)
            }
        }

        pluginManagementRepository.findByIdOrNull(pluginId)?.let {
            it.sourceMarketplaceId = marketplaceId
            pluginManagementRepository.save(it)
        }

        val state = startIfEnabled(pluginId)
        log.info { "Installed plugin '$pluginId' version ${release.version} (state: $state)" }
        return state
    }

    private fun rollback(pluginId: String, loadedId: String?, target: Path, previousPath: Path?) {
        try {
            // The new file may have been loaded under a different ID than expected
            setOfNotNull(pluginId, loadedId).forEach {
                if (pluginManager.getPlugin(it) != null) pluginManager.unloadPlugin(it)
            }
            Files.deleteIfExists(target)
            if (previousPath != null && previousPath.exists()) {
                pluginManager.loadPluginOrThrow(previousPath)
                startIfEnabled(pluginId)
            }
        } catch (e: Exception) {
            log.error(e) { "Failed to restore previous version of plugin '$pluginId'" }
        }
    }

    private fun startIfEnabled(pluginId: String): PluginState {
        val pluginWrapper = pluginManager.getPlugin(pluginId) ?: return PluginState.UNLOADED
        val enabled = pluginManagementRepository.findByIdOrNull(pluginId)?.enabled ?: false

        if (enabled && pluginWrapper.pluginState != PluginState.STARTED && pluginManager.isCompatible(pluginWrapper)) {
            return pluginManager.startPlugin(pluginId) ?: pluginWrapper.pluginState
        }
        return pluginWrapper.pluginState
    }

    private fun move(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }

        // Temporary files are only readable by the owner, use the default permissions for plugin files instead
        try {
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-r--r--"))
        } catch (_: UnsupportedOperationException) {
            // Not a POSIX file system
        }
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    private fun deletePath(path: Path) {
        try {
            if (path.isDirectory()) path.deleteRecursively() else Files.deleteIfExists(path)
        } catch (e: Exception) {
            log.warn { "Failed to delete old plugin file '$path': ${e.message}" }
        }
    }

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    private fun cleanUpDownloadDirectory() {
        try {
            val directory = downloadDirectory
            if (directory.exists()) directory.deleteRecursively()
        } catch (e: Exception) {
            log.warn { "Failed to clean up plugin download directory: ${e.message}" }
        }
    }

    private fun compareVersions(v1: String?, v2: String?): Int {
        if (v1 == null || v2 == null) return compareValues(v1, v2)
        return try {
            pluginManager.versionManager.compareVersions(v1, v2)
        } catch (_: Exception) {
            v1.compareTo(v2)
        }
    }

    private fun sanitize(value: String): String = value.replace(UNSAFE_FILE_NAME_CHARS, "_")
}
