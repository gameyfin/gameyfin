package de.grimsi.gameyfin.core.plugins.management

import de.grimsi.gameyfin.core.plugins.config.PluginConfigRepository
import de.grimsi.gameyfin.pluginapi.core.GameyfinPlugin
import io.github.oshai.kotlinlogging.KotlinLogging
import org.pf4j.*
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import java.io.InputStream
import java.nio.file.Path
import java.security.PublicKey
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.jar.JarFile
import kotlin.io.path.Path
import kotlin.io.path.extension


/**
 * @see https://stackoverflow.com/questions/73654174/my-application-cant-find-the-extension-with-pf4j
 */
@Component
class GameyfinPluginManager(
    val pluginConfigRepository: PluginConfigRepository,
    val dbPluginStatusProvider: DatabasePluginStatusProvider,
    val pluginManagementRepository: PluginManagementRepository
) : DefaultPluginManager(Path(System.getProperty("pf4j.pluginsDir", "plugins"))) {

    companion object {
        private const val PUBLIC_KEY_FILE = "certificates/gameyfin-plugins.pem"
    }

    private val log = KotlinLogging.logger {}
    private val publicKey: PublicKey = loadPluginSignaturePublicKey()

    // This took me way too long to figure out...
    // But I learned a lot about Kotlin and Java interoperability in the process
    init {
        pluginStatusProvider = dbPluginStatusProvider

        pluginStateListeners.add { event ->
            if (event is PluginStateEvent) {
                log.info { "Plugin ${event.plugin.pluginId} changed state to ${event.pluginState}" }
                if (event.oldState == PluginState.DISABLED) {
                    startPlugin(event.plugin.pluginId)
                } else if (event.pluginState == PluginState.DISABLED) {
                    stopPlugin(event.plugin.pluginId)
                }
            }
        }
    }

    override fun createPluginLoader(): PluginLoader {
        val compoundPluginLoader = CompoundPluginLoader()
        val developmentPluginLoader = GameyfinPluginLoader(this, javaClass.classLoader)
        val jarPluginLoader = JarPluginLoader(this)

        return compoundPluginLoader
            .add(developmentPluginLoader, this::isDevelopment)
            .add(jarPluginLoader, this::isNotDevelopment)
    }

    override fun createPluginStatusProvider(): PluginStatusProvider {
        return dbPluginStatusProvider
    }

    override fun loadPluginFromPath(pluginPath: Path?): PluginWrapper? {
        val pluginWrapper = super.loadPluginFromPath(pluginPath)

        if (pluginWrapper == null || pluginPath == null) return null

        // Inject config after loading, before starting
        configurePlugin(pluginWrapper)

        var pluginManagementEntry = pluginManagementRepository.findByIdOrNull(pluginWrapper.pluginId)

        if (pluginManagementEntry == null) {
            // Create a new entry

            // Set priority to the max value of the current plugins + 1 (which is the lowest priority) or 1 if there are no entries
            val currentMaxPriority = pluginManagementRepository.findMaxPriority() ?: 0

            pluginManagementEntry =
                PluginManagementEntry(pluginId = pluginWrapper.pluginId, priority = currentMaxPriority + 1)

            pluginManagementEntry.trustLevel = when (pluginPath.extension) {
                "jar" -> verifyPluginSignature(pluginPath)
                else -> PluginTrustLevel.BUNDLED
            }

            // If the plugin is official or bundled, we can enable it and start it by default
            if (pluginManagementEntry.trustLevel == PluginTrustLevel.OFFICIAL
                || pluginManagementEntry.trustLevel == PluginTrustLevel.BUNDLED
            ) {
                pluginManagementEntry.enabled = true
                log.info { "Plugin ${pluginWrapper.pluginId} verified, starting" }
                startPlugin(pluginWrapper.pluginId)
            }
        } else {
            // Just re-verify the plugin if it was already in the database
            pluginManagementEntry.trustLevel = when (pluginPath.extension) {
                "jar" -> verifyPluginSignature(pluginPath)
                else -> PluginTrustLevel.BUNDLED
            }
        }

        log.debug { "Plugin ${pluginWrapper.pluginId} verification status: ${pluginManagementEntry.trustLevel}" }
        pluginManagementRepository.save(pluginManagementEntry)

        return pluginWrapper
    }

    override fun startPlugin(pluginId: String?): PluginState? {
        if (pluginId == null) return PluginState.FAILED

        // Validate config before starting the plugin
        if (!validatePluginConfig(pluginId)) {
            log.warn { "Plugin $pluginId has invalid configuration" }

            val pluginWrapper = getPlugin(pluginId)
            pluginWrapper.pluginState = PluginState.FAILED
            this.firePluginStateEvent(PluginStateEvent(this, pluginWrapper, pluginWrapper.pluginState))
            return pluginWrapper.pluginState
        }

        return super.startPlugin(pluginId)
    }

    override fun startPlugins() {
        for (pluginWrapper in resolvedPlugins) {
            val pluginState = pluginWrapper.pluginState
            if (!pluginState.isDisabled && !pluginState.isStarted) {

                // Validate config before starting the plugin
                if (!validatePluginConfig(pluginWrapper.pluginId)) {
                    log.error { "Plugin ${pluginWrapper.pluginId} has invalid configuration" }
                    pluginWrapper.pluginState = PluginState.FAILED

                    firePluginStateEvent(PluginStateEvent(this, pluginWrapper, pluginState))
                    return
                }

                try {
                    log.info { "Start plugin '${getPluginLabel(pluginWrapper.descriptor)}'" }
                    pluginWrapper.plugin.start()
                    pluginWrapper.pluginState = PluginState.STARTED
                    pluginWrapper.failedException = null
                    startedPlugins.add(pluginWrapper)
                } catch (e: Exception) {
                    pluginWrapper.pluginState = PluginState.FAILED
                    pluginWrapper.failedException = e
                    log.error { "Unable to start plugin '${getPluginLabel(pluginWrapper.descriptor)}': $e" }
                } finally {
                    firePluginStateEvent(PluginStateEvent(this, pluginWrapper, pluginState))
                }
            }
        }
    }

    fun restart(pluginId: String) {
        val plugin = getPlugin(pluginId)?.plugin ?: return
        stopPlugin(pluginId)
        (plugin as GameyfinPlugin).loadConfig(getConfig(pluginId))
        startPlugin(pluginId)
    }

    fun validatePluginConfig(pluginId: String): Boolean {
        val plugin = getPlugin(pluginId)?.plugin ?: return false
        if (plugin is GameyfinPlugin) {
            return plugin.validateConfig()
        }
        return false
    }

    private fun configurePlugin(pluginWrapper: PluginWrapper) {
        val plugin = pluginWrapper.plugin
        if (plugin is GameyfinPlugin) {
            val config = getConfig(pluginWrapper.pluginId)
            plugin.loadConfig(config)
        }
    }

    private fun getConfig(pluginId: String): Map<String, String?> {
        return pluginConfigRepository.findAllById_PluginId(pluginId).associate { it.id.key to it.value }
    }

    private fun loadPluginSignaturePublicKey(): PublicKey {
        val certFactory: CertificateFactory = CertificateFactory.getInstance("X.509")
        val certFileInputStream = javaClass.classLoader.getResourceAsStream(PUBLIC_KEY_FILE)
        val cert: X509Certificate = certFactory.generateCertificate(certFileInputStream) as X509Certificate
        certFileInputStream?.close()
        return cert.publicKey
    }

    private fun verifyPluginSignature(pluginPath: Path): PluginTrustLevel {
        val jarFile = JarFile(pluginPath.toFile(), true)
        val entries = jarFile.entries()

        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory || entry.name.startsWith("META-INF/")) continue

            try {
                val buffer = ByteArray(8192)
                val entryInputStream: InputStream = jarFile.getInputStream(entry)
                while ((entryInputStream.read(buffer, 0, buffer.size)) != -1) {
                    // We just read
                    // This will throw a SecurityException if a signature/digest check fails
                }
            } catch (_: SecurityException) {
                // Signature verification failed
                return PluginTrustLevel.UNTRUSTED
            }

            val codeSigners = entry.codeSigners

            if (codeSigners == null || codeSigners.isEmpty()) {
                // No code signers, so we can't verify the signature
                return PluginTrustLevel.THIRD_PARTY
            }

            for (codeSigner in codeSigners) {
                val certs = codeSigner.signerCertPath.certificates

                for (cert in certs) {
                    if (cert is X509Certificate) {
                        try {
                            cert.verify(publicKey)
                        } catch (_: Exception) {
                            // Signature verification failed
                            return PluginTrustLevel.UNTRUSTED
                        }
                    }
                }
            }
        }
        return PluginTrustLevel.OFFICIAL
    }
}