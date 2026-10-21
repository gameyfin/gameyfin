package org.gameyfin.app.core.plugins.management

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.core.plugins.config.PluginConfigRepository
import org.gameyfin.pluginapi.core.config.Configurable
import org.gameyfin.pluginapi.core.config.PluginConfigValidationResult
import org.gameyfin.pluginapi.core.config.PluginConfigValidationResultType
import org.gameyfin.pluginapi.data.GameyfinDataService
import org.pf4j.*
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import kotlin.io.path.Path
import kotlin.io.path.extension
import kotlin.reflect.KClass


/**
 * @see "https://stackoverflow.com/questions/73654174/my-application-cant-find-the-extension-with-pf4j"
 */
@Component
class GameyfinPluginManager(
    forwardingPluginStateListener: SpringPluginStateListener,
    private val dbPluginStatusProvider: DatabasePluginStatusProvider,
    private val pluginConfigRepository: PluginConfigRepository,
    private val pluginManagementRepository: PluginManagementRepository,
    private val pluginDataService: ObjectProvider<GameyfinDataService>,
    @Value($$"${gameyfin.plugins.install-directory:$$DEFAULT_INSTALLED_PLUGINS_DIR}")
    installedPluginsDirectory: String = DEFAULT_INSTALLED_PLUGINS_DIR,
    @Value($$"${spring.application.version:}")
    applicationVersion: String = ""
) : DefaultPluginManager(
    listOf(
        Path(System.getProperty("pf4j.pluginsDir", "plugins")),
        Path(installedPluginsDirectory)
    )
) {

    companion object {
        private const val PUBLIC_KEY_FILE = "certificates/gameyfin-plugins.pem"
        const val DEFAULT_INSTALLED_PLUGINS_DIR = "plugins-installed"
        private const val UNKNOWN_SYSTEM_VERSION = "0.0.0"
        private val log = KotlinLogging.logger {}
    }

    private val publicKey: PublicKey = loadPluginSignaturePublicKey()
    private val signatureVerifier = PluginSignatureVerifier(publicKey)

    /**
     * Root directory for plugins installed via the plugin marketplace
     */
    val installedPluginsRoot: Path = Path(installedPluginsDirectory).toAbsolutePath().normalize()

    init {
        // Enables the PF4J compatibility checks ("Plugin-Requires" in the plugin manifest)
        systemVersion = toSystemVersion(applicationVersion)
        log.info { "Plugin system version: $systemVersion" }

        // This took me way too long to figure out...
        // But I learned a lot about Kotlin and Java interoperability in the process
        pluginStatusProvider = dbPluginStatusProvider

        // Plugin state listener to auto-start/stop plugins
        pluginStateListeners.add { event ->
            if (event is PluginStateEvent) {
                log.info { "Plugin ${event.plugin.pluginId} changed state to ${event.pluginState}" }
                val isLoaded = event.pluginState != PluginState.UNLOADED && getPlugin(event.plugin.pluginId) != null
                if (!isLoaded) return@add
                if (event.oldState == PluginState.DISABLED) {
                    startPlugin(event.plugin.pluginId)
                } else if (event.pluginState == PluginState.DISABLED) {
                    stopPlugin(event.plugin.pluginId)
                }
            }
        }

        // Plugin state listener to forward events into Spring context
        addPluginStateListener(forwardingPluginStateListener)
    }

    override fun createPluginLoader(): PluginLoader {
        val pluginLoader = CompoundPluginLoader()

        val jarPluginLoader = GameyfinJarPluginLoader(this)
        pluginLoader.add(jarPluginLoader)

        if (this.isDevelopment) {
            val classPluginLoader = GameyfinDevelopmentPluginLoader(this, javaClass.classLoader)
            pluginLoader.add(classPluginLoader)
        }

        return pluginLoader
    }

    /**
     * Note: Called from the constructor of the superclass, so the properties of this class are not initialized yet.
     * They must only be accessed lazily, i.e. when the plugin paths are requested.
     */
    override fun createPluginRepository(): PluginRepository? {
        val compoundPluginRepository = CompoundPluginRepository()
            .add(JarPluginRepository(getPluginsRoots()))
            .add(DefaultPluginRepository(getPluginsRoots()))
            .add(DevelopmentPluginRepository(getPluginsRoots())) { this.isDevelopment }

        return HighestVersionPluginRepository(
            delegate = compoundPluginRepository,
            descriptorFinder = { pluginDescriptorFinder },
            isCompatible = { isCompatible(it.requires) },
            compareVersions = { v1, v2 -> versionManager.compareVersions(v1, v2) },
            preferredRoot = { installedPluginsRoot }
        )
    }

    /**
     * Note: Called from the constructor of the superclass, so the properties of this class are not initialized yet.
     * They must only be accessed lazily, i.e. when a plugin is instantiated.
     */
    override fun createPluginFactory(): PluginFactory {
        return GameyfinPluginFactory(::createPluginContext)
    }

    private fun createPluginContext(pluginWrapper: PluginWrapper): GameyfinPluginContext {
        return GameyfinPluginContext(
            pluginId = pluginWrapper.pluginId,
            pluginVersion = pluginWrapper.descriptor.version,
            dataDirectory = GameyfinPluginContext.dataDirectoryOf(pluginWrapper.pluginId),
            dataServiceProvider = { pluginDataService.getObject() }
        )
    }

    override fun createPluginStatusProvider(): PluginStatusProvider {
        return dbPluginStatusProvider
    }

    override fun createPluginDescriptorFinder(): PluginDescriptorFinder {
        return GameyfinManifestPluginDescriptorFinder()
    }

    override fun createExtensionFactory(): ExtensionFactory {
        return SingletonExtensionFactory(this)
    }

    override fun createExtensionFinder(): ExtensionFinder? {
        val extensionFinder = GameyfinExtensionFinder(this)
        addPluginStateListener(extensionFinder)
        return extensionFinder
    }

    public override fun loadPluginFromPath(pluginPath: Path): PluginWrapper? {
        return loadPluginFromPath(pluginPath, throwOnError = false)
    }

    /**
     * Loads a single plugin (e.g. one installed via the marketplace), resolves its dependencies and returns its ID.
     * In contrast to [loadPlugin], errors are propagated instead of being swallowed.
     */
    fun loadPluginOrThrow(pluginPath: Path): String {
        val pluginWrapper = loadPluginFromPath(pluginPath, throwOnError = true)
            ?: throw PluginRuntimeException("Could not load plugin from '{}'", pluginPath)
        resolvePlugins()
        return pluginWrapper.pluginId
    }

    private fun loadPluginFromPath(pluginPath: Path, throwOnError: Boolean): PluginWrapper? {

        if (pluginPath.endsWith("data") || pluginPath.endsWith("state")) {
            log.info { "Skipping non-plugin path '$pluginPath'" }
            return null
        }

        val pluginWrapper = try {
            superLoadPluginFromPath(pluginPath)
        } catch (e: Exception) {
            log.error { "Failed to load plugin '$pluginPath': ${e.message}" }
            if (throwOnError) throw e
            null
        }

        if (pluginWrapper == null) return null

        // Re-verify existing entries, create and start new ones
        val pluginManagementEntry = pluginManagementRepository.findByIdOrNull(pluginWrapper.pluginId)
            ?.apply { trustLevel = verifyTrustLevel(pluginPath) }
            ?: createManagementEntry(pluginWrapper, pluginPath)

        // If the plugin is untrusted, we disable it regardless of the previous state
        if (pluginManagementEntry.trustLevel == PluginTrustLevel.UNTRUSTED) {
            log.warn { "Plugin ${pluginWrapper.pluginId} is untrusted, disabling" }
            pluginManagementEntry.enabled = false
        }

        // Inject config after loading and verification, before starting
        // Note: If the plugin is untrusted, we don't want to inject the config
        if (pluginManagementEntry.trustLevel != PluginTrustLevel.UNTRUSTED) configurePlugin(pluginWrapper)

        log.debug { "Plugin ${pluginWrapper.pluginId} verification status: ${pluginManagementEntry.trustLevel}" }
        pluginManagementRepository.save(pluginManagementEntry)

        return pluginWrapper
    }

    /**
     * Creates a new management entry for a plugin that is not yet in the database.
     * Official and bundled plugins are enabled and started right away.
     */
    private fun createManagementEntry(pluginWrapper: PluginWrapper, pluginPath: Path): PluginManagementEntry {
        // Set priority to the max value of the current plugins + 1 (which is the lowest priority) or 1 if there are no entries
        val currentMaxPriority = pluginManagementRepository.findMaxPriority() ?: 0

        val pluginManagementEntry =
            PluginManagementEntry(pluginId = pluginWrapper.pluginId, priority = currentMaxPriority + 1)
        pluginManagementEntry.trustLevel = verifyTrustLevel(pluginPath)

        // If the plugin is official or bundled, we can enable it and start it by default
        if (pluginManagementEntry.trustLevel == PluginTrustLevel.OFFICIAL
            || pluginManagementEntry.trustLevel == PluginTrustLevel.BUNDLED
        ) {
            pluginManagementEntry.enabled = true
            log.info { "Plugin ${pluginWrapper.pluginId} verified, starting" }
            // Save management entry before starting, so startPlugin can find it in the database
            pluginManagementRepository.save(pluginManagementEntry)
            startPlugin(pluginWrapper.pluginId)
        }

        return pluginManagementEntry
    }

    private fun verifyTrustLevel(pluginPath: Path): PluginTrustLevel {
        return when (pluginPath.extension) {
            "jar" -> signatureVerifier.verifyPluginSignature(pluginPath)
            else -> PluginTrustLevel.BUNDLED
        }
    }


    override fun startPlugin(pluginId: String?): PluginState? {
        if (pluginId == null) return PluginState.FAILED

        val trustLevel = pluginManagementRepository.findByIdOrNull(pluginId)?.trustLevel ?: PluginTrustLevel.UNKNOWN
        when (trustLevel) {
            PluginTrustLevel.UNTRUSTED -> {
                val pluginWrapper = getPlugin(pluginId)
                val pluginState = PluginState.UNLOADED
                this.firePluginStateEvent(PluginStateEvent(this, pluginWrapper, pluginState))
                return pluginState
            }

            PluginTrustLevel.UNKNOWN -> {
                val pluginWrapper = getPlugin(pluginId)
                log.warn { "Plugin $pluginId has unknown trust level, not starting" }
                return pluginWrapper?.pluginState
            }

            else -> Unit
        }

        // Validate config before starting the plugin
        if (validatePluginConfig(pluginId).result == PluginConfigValidationResultType.INVALID) {
            log.warn { "Plugin $pluginId has invalid configuration" }

            val pluginWrapper = getPlugin(pluginId)
            pluginWrapper.pluginState = PluginState.FAILED
            this.firePluginStateEvent(PluginStateEvent(this, pluginWrapper, pluginWrapper.pluginState))
            return pluginWrapper.pluginState
        }

        return super.startPlugin(pluginId)
    }

    /**
     * Same as [DefaultPluginManager.deletePlugin], but also works for plugins whose plugin class can't be
     * instantiated (PF4J would fail with a NullPointerException when notifying the plugin about its deletion).
     */
    override fun deletePlugin(pluginId: String?): Boolean {
        val pluginWrapper = getPlugin(pluginId) ?: return super.deletePlugin(pluginId)
        if (runCatching { pluginWrapper.plugin }.getOrNull() != null) return super.deletePlugin(pluginId)

        log.warn { "Plugin $pluginId could not be instantiated, deleting it without notifying the plugin" }
        if (stopPlugin(pluginId)?.isFailed == true) return false
        if (!unloadPlugin(pluginId)) return false
        return pluginRepository.deletePluginPath(pluginWrapper.pluginPath)
    }

    override fun startPlugins() {
        val pluginsToStart = resolvedPlugins.filter { !it.pluginState.isDisabled && !it.pluginState.isStarted }
        pluginsToStart.forEach { startPlugin(it.pluginId) }
    }

    fun restart(pluginId: String) {
        val plugin = getPlugin(pluginId)?.plugin ?: return
        stopPlugin(pluginId)
        if (plugin is Configurable) plugin.loadConfig(getConfig(pluginId))
        startPlugin(pluginId)
    }

    /**
     * Validates [configToValidate] or, if not given, the stored config of the plugin.
     * Untrusted plugins never receive their stored config (same as in [loadPluginFromPath]).
     */
    fun validatePluginConfig(
        pluginId: String,
        configToValidate: Map<String, String?>? = null
    ): PluginConfigValidationResult {
        val plugin = try {
            getPlugin(pluginId)?.plugin
        } catch (_: NoClassDefFoundError) {
            return PluginConfigValidationResult(PluginConfigValidationResultType.UNKNWOWN)
        }

        if (plugin !is Configurable) {
            return PluginConfigValidationResult(PluginConfigValidationResultType.VALID)
        }

        val config = configToValidate ?: when (pluginManagementRepository.findByIdOrNull(pluginId)?.trustLevel) {
            PluginTrustLevel.UNTRUSTED -> emptyMap()
            else -> getConfig(pluginId)
        }

        return plugin.validateConfig(config)
    }

    fun supportsExtensionType(pluginId: String, extensionType: KClass<out ExtensionPoint>): Boolean {
        return getExtensionTypeClasses(pluginId).any { it == extensionType.java }
    }

    fun getExtensionTypeClasses(pluginId: String): List<Class<ExtensionPoint>> {
        return getExtensionClasses(pluginId)
            .flatMap { it.interfaces.toList() }
            .filterIsInstance<Class<ExtensionPoint>>()
    }

    fun getExtensionTypes(pluginId: String): List<String> {
        return getExtensionClasses(pluginId)
            .flatMap { it.interfaces.toList() }
            .filterIsInstance<Class<ExtensionPoint>>()
            .map { it.simpleName }
    }

    fun getPluginsForExtension(extensionClass: KClass<out ExtensionPoint>): List<PluginWrapper> {
        return getPlugins().filter { pluginWrapper ->
            supportsExtensionType(pluginWrapper.pluginId, extensionClass)
        }
    }

    fun getPluginForExtension(extensionClass: KClass<out ExtensionPoint>): PluginWrapper? {
        return getPluginsForExtension(extensionClass).firstOrNull()
    }

    /**
     * Checks if a version constraint (e.g. the "Plugin-Requires" manifest entry) is satisfied by this Gameyfin instance.
     * Mirrors the logic of [AbstractPluginManager.isPluginValid].
     */
    fun isCompatible(requires: String?): Boolean {
        if (systemVersion == UNKNOWN_SYSTEM_VERSION) return true

        var constraint = requires?.trim().orEmpty()
        if (constraint.isEmpty() || constraint == "*") return true
        if (!isExactVersionAllowed && constraint.matches(Regex("^\\d+\\.\\d+\\.\\d+$"))) {
            constraint = ">=$constraint"
        }

        return try {
            versionManager.checkVersionConstraint(systemVersion, constraint)
        } catch (e: Exception) {
            log.warn { "Invalid version constraint '$requires': ${e.message}" }
            false
        }
    }

    fun isCompatible(pluginWrapper: PluginWrapper): Boolean {
        return isCompatible(pluginWrapper.descriptor.requires)
    }

    /**
     * Returns whether the plugin was installed via the marketplace or is a local plugin (bundled, mounted manually or
     * loaded in development mode).
     */
    fun getPluginOrigin(pluginId: String): PluginOrigin {
        val pluginPath = getPlugin(pluginId)?.pluginPath ?: return PluginOrigin.LOCAL
        return if (isInInstalledPluginsRoot(pluginPath)) PluginOrigin.MARKETPLACE else PluginOrigin.LOCAL
    }

    fun isInInstalledPluginsRoot(path: Path): Boolean {
        return path.toAbsolutePath().normalize().startsWith(installedPluginsRoot)
    }

    /**
     * Finds plugin files of the given plugin ID in the local plugin roots (excluding the marketplace root).
     * Used to fall back to a bundled version after a marketplace version has been removed.
     */
    fun findLocalPluginPath(pluginId: String): Path? {
        val repository = (pluginRepository as? HighestVersionPluginRepository)?.delegate ?: pluginRepository
        return repository.pluginPaths
            .filterNot { isInInstalledPluginsRoot(it) }
            .firstOrNull { path ->
                try {
                    pluginDescriptorFinder.isApplicable(path) && pluginDescriptorFinder.find(path).pluginId == pluginId
                } catch (_: Exception) {
                    false
                }
            }
    }

    fun readPluginDescriptor(pluginPath: Path): PluginDescriptor? {
        return try {
            pluginDescriptorFinder.find(pluginPath)
        } catch (_: Exception) {
            null
        }
    }

    fun getManagementEntry(pluginId: String): PluginManagementEntry {
        return pluginManagementRepository.findByIdOrNull(pluginId)
            ?: throw IllegalArgumentException("Plugin with ID $pluginId not found")
    }

    private fun configurePlugin(pluginWrapper: PluginWrapper) {
        val plugin = pluginWrapper.plugin
        if (plugin is Configurable) {
            val config = getConfig(pluginWrapper.pluginId)
            plugin.loadConfig(config)
        }
    }

    private fun getConfig(pluginId: String): Map<String, String?> {
        return pluginConfigRepository.findAllByPluginId(pluginId).associate { it.id.key to it.value }
    }

    private fun loadPluginSignaturePublicKey(): PublicKey {
        val certFactory: CertificateFactory = CertificateFactory.getInstance("X.509")
        val certFileInputStream = javaClass.classLoader.getResourceAsStream(PUBLIC_KEY_FILE)
        val cert: X509Certificate = certFactory.generateCertificate(certFileInputStream) as X509Certificate
        certFileInputStream?.close()
        return cert.publicKey
    }


    /**
     * Converts the application version to a version usable for compatibility checks.
     * Pre-release and build metadata are stripped, so preview builds are treated like their final release.
     */
    private fun toSystemVersion(applicationVersion: String): String {
        val match = Regex("^(\\d+\\.\\d+\\.\\d+)").find(applicationVersion.trim())
        return match?.groupValues?.get(1) ?: UNKNOWN_SYSTEM_VERSION
    }

    // Needed for unit testing since super.loadPluginFromPath is protected
    internal fun superLoadPluginFromPath(pluginPath: Path): PluginWrapper? {
        return super.loadPluginFromPath(pluginPath)
    }
}
