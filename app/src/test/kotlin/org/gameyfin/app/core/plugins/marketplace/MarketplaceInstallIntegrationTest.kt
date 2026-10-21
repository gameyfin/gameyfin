package org.gameyfin.app.core.plugins.marketplace

import com.sun.net.httpserver.HttpServer
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.gameyfin.app.core.plugins.config.PluginConfigRepository
import org.gameyfin.app.core.plugins.management.*
import org.gameyfin.pluginapi.data.GameyfinDataService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pf4j.Plugin
import org.pf4j.update.VerifyException
import org.springframework.beans.factory.ObjectProvider
import java.net.InetSocketAddress
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.*

/**
 * End-to-end test of the marketplace installation: a real [GameyfinPluginManager] and [GameyfinUpdateManager]
 * (with the real repository, downloader and verifiers) install plugin JARs served by a local HTTP server.
 *
 * The generated plugin JARs are unsigned, so PF4J loads them as UNTRUSTED plugins (disabled, never started).
 * That is sufficient to verify the file handling and loading/unloading in PF4J.
 */
class MarketplaceInstallIntegrationTest {

    companion object {
        private const val PLUGIN_ID = "integration-plugin"
        private const val MARKETPLACE_ID = "test-market"
    }

    @TempDir
    lateinit var tempDir: Path

    private lateinit var pluginsDir: Path
    private lateinit var installedDir: Path
    private lateinit var servedDir: Path

    private lateinit var server: HttpServer
    private val served = ConcurrentHashMap<String, ByteArray>()
    private val requestedPaths: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private val managementEntries = ConcurrentHashMap<String, PluginManagementEntry>()
    private lateinit var pluginManagementRepository: PluginManagementRepository
    private lateinit var pluginManager: GameyfinPluginManager
    private lateinit var updateManager: GameyfinUpdateManager

    @BeforeEach
    fun setup() {
        pluginsDir = Files.createDirectories(tempDir.resolve("plugins"))
        installedDir = Files.createDirectories(tempDir.resolve("plugins-installed"))
        servedDir = Files.createDirectories(tempDir.resolve("served"))

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            exchange.use {
                requestedPaths.add(it.requestURI.path)
                val body = served[it.requestURI.path]
                if (body == null) {
                    it.sendResponseHeaders(404, -1)
                } else {
                    it.sendResponseHeaders(200, body.size.toLong())
                    it.responseBody.write(body)
                }
            }
        }
        server.start()

        pluginManagementRepository = mockk()
        every { pluginManagementRepository.findById(any()) } answers {
            Optional.ofNullable(managementEntries[firstArg()])
        }
        every { pluginManagementRepository.save(any<PluginManagementEntry>()) } answers {
            val entry = firstArg<PluginManagementEntry>()
            managementEntries[entry.pluginId] = entry
            entry
        }
        every { pluginManagementRepository.findMaxPriority() } answers {
            managementEntries.values.maxOfOrNull { it.priority }
        }

        val pluginConfigRepository = mockk<PluginConfigRepository>()
        every { pluginConfigRepository.findAllByPluginId(any()) } returns emptyList()

        System.setProperty("pf4j.pluginsDir", pluginsDir.toString())
        pluginManager = GameyfinPluginManager(
            mockk<SpringPluginStateListener>(relaxed = true),
            DatabasePluginStatusProvider(pluginManagementRepository),
            pluginConfigRepository,
            pluginManagementRepository,
            mockk<ObjectProvider<GameyfinDataService>>(relaxed = true),
            installedPluginsDirectory = installedDir.toString(),
            applicationVersion = "2.0.0"
        )

        updateManager = GameyfinUpdateManager(
            pluginManager,
            MarketplaceHttpClient(allowInsecureUrls = true),
            pluginManagementRepository
        )
        updateManager.setMarketplaceRepositories(
            listOf(updateManager.createRepository(MARKETPLACE_ID, URI("${baseUrl()}/plugins.json")))
        )
    }

    @AfterEach
    fun tearDown() {
        try {
            pluginManager.stopPlugins()
            pluginManager.unloadPlugins()
        } catch (_: Exception) {
            // Ignore cleanup errors
        }
        server.stop(0)
        System.clearProperty("pf4j.pluginsDir")
        unmockkAll()
    }

    // ========================================================================================
    // Tests
    // ========================================================================================

    @Test
    fun `install, update and uninstall should manage plugin files and fall back to the bundled version`() {
        createPluginJar(pluginsDir.resolve("$PLUGIN_ID-1.0.0.jar"), PLUGIN_ID, "1.0.0")
        pluginManager.loadPlugins()
        assertEquals("1.0.0", loadedVersion())
        assertEquals(PluginOrigin.LOCAL, pluginManager.getPluginOrigin(PLUGIN_ID))

        publishIndex(
            serveRelease("1.1.0"),
            serveRelease("1.2.0"),
        )

        // Install 1.1.0
        updateManager.installPlugin(MARKETPLACE_ID, PLUGIN_ID, "1.1.0")

        val installed110 = installedDir.resolve("$PLUGIN_ID-1.1.0.jar")
        assertTrue(installed110.isRegularFile())
        assertEquals("1.1.0", loadedVersion())
        assertEquals(installed110, pluginManager.getPlugin(PLUGIN_ID).pluginPath.toAbsolutePath().normalize())
        assertEquals(PluginOrigin.MARKETPLACE, pluginManager.getPluginOrigin(PLUGIN_ID))
        assertTrue(pluginsDir.resolve("$PLUGIN_ID-1.0.0.jar").exists(), "Bundled plugin file must be kept")
        assertEquals(MARKETPLACE_ID, managementEntries[PLUGIN_ID]?.sourceMarketplaceId)
        assertFalse(managementEntries.getValue(PLUGIN_ID).enabled, "Unsigned plugins must not be enabled")
        assertNoTemporaryFiles()
        assertTrue(requestedPaths.contains("/$PLUGIN_ID-1.1.0.jar"))

        // Update to the latest version
        assertTrue(updateManager.hasPluginUpdate(PLUGIN_ID))
        updateManager.installPlugin(MARKETPLACE_ID, PLUGIN_ID, null)

        val installed120 = installedDir.resolve("$PLUGIN_ID-1.2.0.jar")
        assertTrue(installed120.isRegularFile())
        assertFalse(installed110.exists(), "Previous marketplace version must be removed")
        assertEquals("1.2.0", loadedVersion())
        assertEquals(1, pluginManager.plugins.count { it.pluginId == PLUGIN_ID })
        assertEquals(MARKETPLACE_ID, managementEntries[PLUGIN_ID]?.sourceMarketplaceId)
        assertFalse(updateManager.hasPluginUpdate(PLUGIN_ID))
        assertNoTemporaryFiles()

        // Installing the same version again is rejected
        val sameVersion = assertFailsWith<IllegalStateException> {
            updateManager.installPlugin(MARKETPLACE_ID, PLUGIN_ID, "1.2.0")
        }
        assertTrue(sameVersion.message!!.contains("already installed"))

        // Uninstall falls back to the bundled version
        updateManager.uninstall(PLUGIN_ID)

        assertFalse(installed120.exists())
        assertEquals("1.0.0", loadedVersion())
        assertEquals(
            pluginsDir.resolve("$PLUGIN_ID-1.0.0.jar"),
            pluginManager.getPlugin(PLUGIN_ID).pluginPath.toAbsolutePath().normalize()
        )
        assertEquals(PluginOrigin.LOCAL, pluginManager.getPluginOrigin(PLUGIN_ID))
        assertNull(managementEntries[PLUGIN_ID]?.sourceMarketplaceId)
        assertTrue(installedJars().isEmpty())

        // A local plugin can't be uninstalled via the marketplace
        assertFailsWith<IllegalStateException> { updateManager.uninstall(PLUGIN_ID) }
    }

    @Test
    fun `fresh install and uninstall without a bundled version should leave no plugin loaded`() {
        pluginManager.loadPlugins()
        publishIndex(serveRelease("1.0.0"))

        updateManager.installPlugin(MARKETPLACE_ID, PLUGIN_ID, "1.0.0")

        assertEquals("1.0.0", loadedVersion())
        assertTrue(installedDir.resolve("$PLUGIN_ID-1.0.0.jar").isRegularFile())
        assertEquals(MARKETPLACE_ID, managementEntries[PLUGIN_ID]?.sourceMarketplaceId)

        assertNull(updateManager.uninstall(PLUGIN_ID))

        assertNull(pluginManager.getPlugin(PLUGIN_ID))
        assertTrue(installedJars().isEmpty())
        assertNull(managementEntries[PLUGIN_ID]?.sourceMarketplaceId)
    }

    @Test
    fun `plugin whose plugin class can't be instantiated should still be uninstallable`() {
        pluginManager.loadPlugins()
        publishIndex(serveRelease("1.0.0", pluginClass = "com.example.DoesNotExist"))

        updateManager.installPlugin(MARKETPLACE_ID, PLUGIN_ID, "1.0.0")
        assertEquals("1.0.0", loadedVersion())

        assertNull(updateManager.uninstall(PLUGIN_ID))

        assertNull(pluginManager.getPlugin(PLUGIN_ID))
        assertTrue(installedJars().isEmpty())
        assertNull(managementEntries[PLUGIN_ID]?.sourceMarketplaceId)
    }

    @Test
    fun `installed plugin should be picked up again by a new plugin manager`() {
        createPluginJar(pluginsDir.resolve("$PLUGIN_ID-1.0.0.jar"), PLUGIN_ID, "1.0.0")
        pluginManager.loadPlugins()
        publishIndex(serveRelease("1.1.0"))

        updateManager.installPlugin(MARKETPLACE_ID, PLUGIN_ID, "1.1.0")
        pluginManager.unloadPlugins()

        // Simulates a restart: the marketplace version takes precedence over the bundled one
        val restarted = GameyfinPluginManager(
            mockk<SpringPluginStateListener>(relaxed = true),
            DatabasePluginStatusProvider(pluginManagementRepository),
            mockk<PluginConfigRepository> { every { findAllByPluginId(any()) } returns emptyList() },
            pluginManagementRepository,
            mockk<ObjectProvider<GameyfinDataService>>(relaxed = true),
            installedPluginsDirectory = installedDir.toString(),
            applicationVersion = "2.0.0"
        )
        try {
            restarted.loadPlugins()
            assertEquals("1.1.0", restarted.getPlugin(PLUGIN_ID)?.descriptor?.version)
            assertEquals(PluginOrigin.MARKETPLACE, restarted.getPluginOrigin(PLUGIN_ID))
        } finally {
            restarted.unloadPlugins()
        }
    }

    @Test
    fun `checksum mismatch should not install anything`() {
        createPluginJar(pluginsDir.resolve("$PLUGIN_ID-1.0.0.jar"), PLUGIN_ID, "1.0.0")
        pluginManager.loadPlugins()

        val release = serveRelease("1.1.0")
        publishIndex(release.copy(sha512 = "0".repeat(128)))

        val exception = assertFailsWith<VerifyException> {
            updateManager.installPlugin(MARKETPLACE_ID, PLUGIN_ID, "1.1.0")
        }
        assertTrue(exception.message!!.contains("checksum mismatch"))

        assertTrue(installedJars().isEmpty())
        assertNoTemporaryFiles()
        assertEquals("1.0.0", loadedVersion())
        assertEquals(PluginOrigin.LOCAL, pluginManager.getPluginOrigin(PLUGIN_ID))
        assertNull(managementEntries[PLUGIN_ID]?.sourceMarketplaceId)
    }

    @Test
    fun `checksum file URL should be supported`() {
        pluginManager.loadPlugins()
        val release = serveRelease("1.0.0")
        served["/$PLUGIN_ID-1.0.0.sha512"] = "${release.sha512}  $PLUGIN_ID-1.0.0.jar\n".toByteArray()
        publishIndex(release.copy(sha512 = ".sha512"))

        updateManager.installPlugin(MARKETPLACE_ID, PLUGIN_ID, "1.0.0")

        assertEquals("1.0.0", loadedVersion())
        assertTrue(requestedPaths.contains("/$PLUGIN_ID-1.0.0.sha512"))
    }

    @Test
    fun `plugin ID mismatch in the downloaded file should not install anything`() {
        pluginManager.loadPlugins()
        val jar = createPluginJar(servedDir.resolve("other.jar"), "other-plugin", "1.0.0")
        served["/other.jar"] = Files.readAllBytes(jar)
        publishIndex(Release("1.0.0", "other.jar", MandatorySha512Verifier.sha512(jar)))

        val exception = assertFailsWith<VerifyException> {
            updateManager.installPlugin(MARKETPLACE_ID, PLUGIN_ID, "1.0.0")
        }
        assertTrue(exception.message!!.contains("Plugin ID mismatch"))

        assertTrue(installedJars().isEmpty())
        assertNoTemporaryFiles()
        assertNull(pluginManager.getPlugin("other-plugin"))
        assertNull(pluginManager.getPlugin(PLUGIN_ID))
    }

    @Test
    fun `missing plugin file should not install anything`() {
        pluginManager.loadPlugins()
        publishIndex(Release("1.0.0", "missing.jar", "0".repeat(128)))

        assertFails { updateManager.installPlugin(MARKETPLACE_ID, PLUGIN_ID, "1.0.0") }

        assertTrue(installedJars().isEmpty())
        assertNoTemporaryFiles()
        assertNull(pluginManager.getPlugin(PLUGIN_ID))
    }

    @Test
    fun `incompatible release should be rejected before downloading`() {
        pluginManager.loadPlugins()
        publishIndex(serveRelease("1.0.0").copy(requires = ">=3.0.0"))

        assertFailsWith<IllegalStateException> {
            updateManager.installPlugin(MARKETPLACE_ID, PLUGIN_ID, "1.0.0")
        }

        assertFalse(requestedPaths.contains("/$PLUGIN_ID-1.0.0.jar"))
        assertTrue(installedJars().isEmpty())
    }

    @Test
    fun `release older than the bundled version should be rejected`() {
        createPluginJar(pluginsDir.resolve("$PLUGIN_ID-2.0.0.jar"), PLUGIN_ID, "2.0.0")
        pluginManager.loadPlugins()
        publishIndex(serveRelease("1.5.0"))

        val exception = assertFailsWith<IllegalStateException> {
            updateManager.installPlugin(MARKETPLACE_ID, PLUGIN_ID, "1.5.0")
        }
        assertTrue(exception.message!!.contains("already provided locally"))
        assertFalse(requestedPaths.contains("/$PLUGIN_ID-1.5.0.jar"))
        assertEquals("2.0.0", loadedVersion())
    }

    @Test
    fun `stale download directory should be removed on startup`() {
        val staleFile = Files.createDirectories(installedDir.resolve(".download")).resolve("plugin-123.jar.part")
        Files.writeString(staleFile, "partial")

        GameyfinUpdateManager(pluginManager, MarketplaceHttpClient(allowInsecureUrls = true), pluginManagementRepository)

        assertFalse(staleFile.exists())
        assertFalse(installedDir.resolve(".download").exists())
    }

    // ========================================================================================
    // Helpers
    // ========================================================================================

    private data class Release(
        val version: String,
        val fileName: String,
        val sha512: String,
        val requires: String = "*",
    )

    private fun baseUrl(): String = "http://127.0.0.1:${server.address.port}"

    private fun loadedVersion(): String? = pluginManager.getPlugin(PLUGIN_ID)?.descriptor?.version

    private fun installedJars(): List<Path> = installedDir.listDirectoryEntries("*.jar")

    private fun assertNoTemporaryFiles() {
        val downloadDir = installedDir.resolve(".download")
        if (downloadDir.exists()) {
            assertTrue(downloadDir.listDirectoryEntries().isEmpty(), "Download directory must be empty")
        }
        assertTrue(installedDir.listDirectoryEntries().none { it.name.endsWith(".part") })
    }

    /**
     * Creates a plugin JAR for [PLUGIN_ID] in the given version and serves it via the HTTP server.
     */
    private fun serveRelease(version: String, pluginClass: String = Plugin::class.java.name): Release {
        val fileName = "$PLUGIN_ID-$version.jar"
        val jar = createPluginJar(servedDir.resolve(fileName), PLUGIN_ID, version, pluginClass = pluginClass)
        served["/$fileName"] = Files.readAllBytes(jar)
        return Release(version, fileName, MandatorySha512Verifier.sha512(jar))
    }

    private fun publishIndex(vararg releases: Release) {
        val releasesJson = releases.joinToString(",") { release ->
            """
            {
              "version": "${release.version}",
              "date": "2024-01-01",
              "requires": "${release.requires}",
              "url": "${release.fileName}",
              "sha512sum": "${release.sha512}"
            }
            """.trimIndent()
        }
        val index = """
            {
              "plugins": [
                {
                  "id": "$PLUGIN_ID",
                  "name": "Integration Plugin",
                  "description": "Plugin for integration tests",
                  "provider": "Gameyfin Tests",
                  "releases": [$releasesJson]
                }
              ]
            }
        """.trimIndent()
        served["/plugins.json"] = index.toByteArray()
    }

    private fun createPluginJar(
        jarPath: Path,
        pluginId: String,
        version: String,
        requires: String = "*",
        pluginClass: String = Plugin::class.java.name
    ): Path {
        val manifest = Manifest()
        manifest.mainAttributes.putValue("Manifest-Version", "1.0")
        manifest.mainAttributes.putValue("Plugin-Id", pluginId)
        manifest.mainAttributes.putValue("Plugin-Version", version)
        manifest.mainAttributes.putValue("Plugin-Class", pluginClass)
        manifest.mainAttributes.putValue("Plugin-Name", "Plugin $pluginId")
        manifest.mainAttributes.putValue("Plugin-Author", "Test Author")
        manifest.mainAttributes.putValue("Plugin-Requires", requires)

        JarOutputStream(Files.newOutputStream(jarPath), manifest).use { jos ->
            jos.putNextEntry(JarEntry("data.txt"))
            jos.write("$pluginId $version".toByteArray())
            jos.closeEntry()
        }
        return jarPath
    }
}
