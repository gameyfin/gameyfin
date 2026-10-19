package org.gameyfin.app.core.plugins.marketplace

import io.mockk.*
import org.gameyfin.app.core.plugins.management.GameyfinPluginManager
import org.gameyfin.app.core.plugins.management.PluginManagementEntry
import org.gameyfin.app.core.plugins.management.PluginManagementRepository
import org.gameyfin.app.core.plugins.management.PluginTrustLevel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pf4j.*
import org.pf4j.update.DefaultUpdateRepository
import org.pf4j.update.FileDownloader
import org.pf4j.update.FileVerifier
import org.pf4j.update.PluginInfo.PluginRelease
import org.pf4j.update.VerifyException
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*
import kotlin.test.*

/**
 * Unit tests for [GameyfinUpdateManager] using a mocked [GameyfinPluginManager] which simulates loading plugins
 * from files containing "<pluginId>:<version>".
 */
class GameyfinUpdateManagerTest {

    companion object {
        private const val PLUGIN_ID = "test-plugin"
        private const val SYSTEM_VERSION = "2.0.0"
    }

    @TempDir
    lateinit var tempDir: Path

    private lateinit var installedRoot: Path
    private lateinit var localRoot: Path
    private lateinit var downloadSourceDir: Path

    private val versionManager = DefaultVersionManager()
    private lateinit var pluginManager: GameyfinPluginManager
    private lateinit var pluginManagementRepository: PluginManagementRepository
    private lateinit var updateManager: GameyfinUpdateManager

    private val entries = mutableMapOf<String, PluginManagementEntry>()
    private val loaded = mutableMapOf<String, PluginWrapper>()

    /** Trust level assigned to new management entries created when a plugin is loaded */
    private var newEntryTrustLevel = PluginTrustLevel.THIRD_PARTY

    /** Paths for which loading fails */
    private val failingLoadPaths = mutableSetOf<Path>()

    private var localPluginPath: Path? = null
    private lateinit var fileDownloader: FileDownloader
    private lateinit var fileVerifier: FileVerifier
    private val downloadedFiles = mutableListOf<Path>()

    @BeforeEach
    fun setup() {
        installedRoot = tempDir.resolve("plugins-installed").createDirectories()
        localRoot = tempDir.resolve("plugins").createDirectories()
        downloadSourceDir = tempDir.resolve("downloads").createDirectories()

        pluginManagementRepository = mockk()
        every { pluginManagementRepository.findById(any()) } answers { Optional.ofNullable(entries[firstArg()]) }
        every { pluginManagementRepository.save(any<PluginManagementEntry>()) } answers {
            firstArg<PluginManagementEntry>().also { entries[it.pluginId] = it }
        }

        pluginManager = mockk(relaxed = true)
        every { pluginManager.versionManager } returns versionManager
        every { pluginManager.systemVersion } returns SYSTEM_VERSION
        every { pluginManager.runtimeMode } returns RuntimeMode.DEPLOYMENT
        every { pluginManager.installedPluginsRoot } returns installedRoot
        every { pluginManager.isCompatible(any<String>()) } answers {
            val requires = firstArg<String?>()?.trim().orEmpty()
            requires.isEmpty() || requires == "*" || versionManager.checkVersionConstraint(SYSTEM_VERSION, requires)
        }
        every { pluginManager.isCompatible(any<PluginWrapper>()) } returns true
        every { pluginManager.getPlugin(any()) } answers { loaded[firstArg()] }
        every { pluginManager.isInInstalledPluginsRoot(any()) } answers {
            firstArg<Path>().toAbsolutePath().normalize().startsWith(installedRoot)
        }
        every { pluginManager.findLocalPluginPath(any()) } answers { localPluginPath }
        every { pluginManager.readPluginDescriptor(any()) } answers {
            val (id, version) = readPluginFile(firstArg())
            DefaultPluginDescriptor(id, "", "Plugin", version, "*", "author", "MIT")
        }
        every { pluginManager.stopPlugin(any()) } answers {
            loaded[firstArg()]?.also { it.pluginState = PluginState.STOPPED }?.pluginState
        }
        every { pluginManager.unloadPlugin(any()) } answers { loaded.remove(firstArg<String>()) != null }
        every { pluginManager.loadPluginOrThrow(any()) } answers { simulateLoad(firstArg()) }
        every { pluginManager.startPlugin(any()) } answers {
            loaded[firstArg()]?.also { it.pluginState = PluginState.STARTED }?.pluginState
        }
        every { pluginManager.disablePlugin(any()) } answers {
            entries[firstArg()]?.enabled = false
            loaded[firstArg()]?.pluginState = PluginState.DISABLED
            true
        }
        every { pluginManager.deletePlugin(any()) } answers {
            val wrapper = loaded.remove(firstArg<String>())
            wrapper?.pluginPath?.deleteIfExists()
            wrapper != null
        }

        fileDownloader = mockk()
        every { fileDownloader.downloadFile(any()) } answers { simulateDownload(firstArg()) }
        fileVerifier = mockk()
        every { fileVerifier.verify(any(), any()) } just Runs

        updateManager = GameyfinUpdateManager(pluginManager, MarketplaceHttpClient(), pluginManagementRepository)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    // ========================================================================================
    // Helpers
    // ========================================================================================

    private fun readPluginFile(path: Path): Pair<String, String> {
        val (id, version) = path.readText().split(":")
        return id to version
    }

    private fun writePluginFile(path: Path, pluginId: String, version: String): Path {
        path.parent.createDirectories()
        path.writeText("$pluginId:$version")
        return path
    }

    private fun simulateLoad(path: Path): String {
        if (path in failingLoadPaths) throw PluginRuntimeException("Simulated load failure for '{}'", path)
        val (id, version) = readPluginFile(path)
        val descriptor = DefaultPluginDescriptor(id, "", "Plugin", version, "*", "author", "MIT")
        val wrapper = PluginWrapper(pluginManager, descriptor, path, javaClass.classLoader)
        wrapper.pluginState = PluginState.RESOLVED
        loaded[id] = wrapper
        if (entries[id] == null) {
            val entry = PluginManagementEntry(id, priority = entries.size + 1, trustLevel = newEntryTrustLevel)
            entry.enabled = newEntryTrustLevel == PluginTrustLevel.OFFICIAL || newEntryTrustLevel == PluginTrustLevel.BUNDLED
            entries[id] = entry
        }
        return id
    }

    /** Download URLs have the form https://example.com/<pluginId>/<version>.jar */
    private fun simulateDownload(url: URL): Path {
        val segments = url.path.split("/").filter { it.isNotEmpty() }
        val id = segments[0]
        val version = segments[1].removeSuffix(".jar")
        val file = Files.createTempFile(downloadSourceDir, "plugin-", ".jar.part")
        writePluginFile(file, id, version)
        downloadedFiles.add(file)
        return file
    }

    private fun release(version: String, requires: String = "*", pluginId: String = PLUGIN_ID): PluginRelease {
        return GameyfinPluginRelease().apply {
            this.version = version
            this.requires = requires
            this.url = "https://example.com/$pluginId/$version.jar"
            this.sha512sum = "a".repeat(128)
            this.date = Date(0)
        }
    }

    private fun pluginInfo(
        repositoryId: String,
        vararg releases: PluginRelease,
        pluginId: String = PLUGIN_ID,
        name: String = pluginId
    ): GameyfinPluginInfo {
        return GameyfinPluginInfo().apply {
            this.id = pluginId
            this.name = name
            this.releases = releases.toList()
            this.repositoryId = repositoryId
        }
    }

    private fun repository(id: String, vararg plugins: GameyfinPluginInfo): GameyfinUpdateRepository {
        val repository = mockk<GameyfinUpdateRepository>()
        every { repository.id } returns id
        every { repository.getGameyfinPlugins() } returns plugins.associateBy { it.id }
        every { repository.fileDownloader } returns fileDownloader
        every { repository.fileVerifier } returns fileVerifier
        every { repository.refresh() } just Runs
        return repository
    }

    private fun setRepositories(vararg repositories: GameyfinUpdateRepository) {
        updateManager.setMarketplaceRepositories(repositories.toList())
    }

    private fun installedFile(version: String, pluginId: String = PLUGIN_ID): Path =
        installedRoot.resolve("$pluginId-$version.jar")

    /** Simulates an already loaded plugin (e.g. installed in a previous run) */
    private fun preload(
        path: Path,
        version: String,
        enabled: Boolean,
        trustLevel: PluginTrustLevel = PluginTrustLevel.THIRD_PARTY,
        sourceMarketplaceId: String? = null,
        state: PluginState = if (enabled) PluginState.STARTED else PluginState.DISABLED
    ): PluginWrapper {
        writePluginFile(path, PLUGIN_ID, version)
        entries[PLUGIN_ID] = PluginManagementEntry(
            PLUGIN_ID,
            enabled = enabled,
            priority = 1,
            trustLevel = trustLevel,
            sourceMarketplaceId = sourceMarketplaceId
        )
        simulateLoad(path)
        return loaded.getValue(PLUGIN_ID).also { it.pluginState = state }
    }

    private fun assertNoDownloadLeftovers() {
        downloadedFiles.forEach { assertFalse(it.exists(), "Downloaded file $it should have been removed") }
    }

    // ========================================================================================
    // Initialization
    // ========================================================================================

    @Test
    fun `init should clean up leftover download directory`() {
        val downloadDir = installedRoot.resolve(".download").createDirectories()
        downloadDir.resolve("plugin-123.jar.part").writeText("partial")
        downloadDir.resolve("nested").createDirectories().resolve("file").writeText("x")
        val installedPlugin = writePluginFile(installedFile("1.0.0"), PLUGIN_ID, "1.0.0")

        GameyfinUpdateManager(pluginManager, MarketplaceHttpClient(), pluginManagementRepository)

        assertFalse(downloadDir.exists())
        assertTrue(installedPlugin.exists())
    }

    @Test
    fun `init should tolerate missing installed plugins root`() {
        every { pluginManager.installedPluginsRoot } returns tempDir.resolve("does-not-exist")

        val manager = GameyfinUpdateManager(pluginManager, MarketplaceHttpClient(), pluginManagementRepository)

        assertTrue(manager.getMarketplaceRepositories().isEmpty())
        assertTrue(manager.repositories.isEmpty())
    }

    // ========================================================================================
    // Repository management
    // ========================================================================================

    @Test
    fun `setMarketplaceRepositories should replace repositories and keep order`() {
        val a = repository("a")
        val b = repository("b")
        val c = repository("c")
        setRepositories(a, b)

        updateManager.setMarketplaceRepositories(listOf(c, a))

        assertEquals(listOf("c", "a"), updateManager.getMarketplaceRepositories().map { it.id })
        assertEquals(listOf("c", "a"), updateManager.repositories.map { it.id })
    }

    @Test
    fun `getMarketplaceRepositories should return a snapshot`() {
        setRepositories(repository("a"))
        val snapshot = updateManager.getMarketplaceRepositories()

        updateManager.addRepository(repository("b"))

        assertEquals(listOf("a"), snapshot.map { it.id })
        assertEquals(listOf("a", "b"), updateManager.getMarketplaceRepositories().map { it.id })
    }

    @Test
    fun `getMarketplaceRepository should find repository by id`() {
        val a = repository("a")
        setRepositories(a, repository("b"))

        assertSame(a, updateManager.getMarketplaceRepository("a"))
        assertNull(updateManager.getMarketplaceRepository("unknown"))
    }

    @Test
    fun `setRepositories should ignore non-marketplace repositories and refresh`() {
        val a = repository("a")
        val foreign = DefaultUpdateRepository("foreign", URI("https://example.com/").toURL())

        updateManager.setRepositories(listOf(a, foreign))

        assertEquals(listOf("a"), updateManager.getMarketplaceRepositories().map { it.id })
        verify(exactly = 1) { a.refresh() }
    }

    @Test
    fun `addRepository should append repository`() {
        setRepositories(repository("a"))

        updateManager.addRepository(repository("b"))

        assertEquals(listOf("a", "b"), updateManager.getMarketplaceRepositories().map { it.id })
    }

    @Test
    fun `addRepository should reject duplicate id`() {
        setRepositories(repository("a"))

        val e = assertFailsWith<IllegalArgumentException> { updateManager.addRepository(repository("a")) }

        assertTrue(e.message!!.contains("already exists"))
        assertEquals(1, updateManager.getMarketplaceRepositories().size)
    }

    @Test
    fun `addRepository should reject non-marketplace repositories`() {
        val foreign = DefaultUpdateRepository("foreign", URI("https://example.com/").toURL())

        assertFailsWith<IllegalArgumentException> { updateManager.addRepository(foreign) }
        assertTrue(updateManager.getMarketplaceRepositories().isEmpty())
    }

    @Test
    fun `addRepository with URL should create marketplace repository`() {
        updateManager.addRepository("new", URI("https://example.com/plugins.json").toURL())

        val repository = updateManager.getMarketplaceRepository("new")
        assertNotNull(repository)
        assertEquals(URI("https://example.com/plugins.json"), repository.indexUrl)
        assertEquals(URI("https://example.com/plugins.json").toURL(), repository.url)
    }

    @Test
    fun `addRepository with URL should reject duplicate id`() {
        updateManager.addRepository("new", URI("https://example.com/plugins.json").toURL())

        assertFailsWith<IllegalArgumentException> {
            updateManager.addRepository("new", URI("https://example.org/plugins.json").toURL())
        }
    }

    @Test
    fun `createRepository should use given id and index URL`() {
        val repository = updateManager.createRepository("id", URI("https://example.com/index.json"))

        assertEquals("id", repository.id)
        assertEquals(URI("https://example.com/index.json"), repository.indexUrl)
        assertTrue(repository.fileDownloader is GameyfinFileDownloader)
        assertTrue(repository.fileVerifier is MarketplaceFileVerifier)
        // Not added automatically
        assertNull(updateManager.getMarketplaceRepository("id"))
    }

    @Test
    fun `removeRepository should remove repository by id`() {
        setRepositories(repository("a"), repository("b"))

        updateManager.removeRepository("a")
        updateManager.removeRepository("unknown")

        assertEquals(listOf("b"), updateManager.getMarketplaceRepositories().map { it.id })
    }

    @Test
    fun `refresh should refresh all repositories`() {
        val a = repository("a")
        val b = repository("b")
        setRepositories(a, b)

        updateManager.refresh()

        verify(exactly = 1) { a.refresh() }
        verify(exactly = 1) { b.refresh() }
    }

    // ========================================================================================
    // Plugin lookup
    // ========================================================================================

    @Test
    fun `getPluginsMap should merge repositories with the first repository taking precedence`() {
        val officialInfo = pluginInfo("official", release("1.0.0"), name = "Official")
        val thirdPartyInfo = pluginInfo("third", release("9.0.0"), name = "Third party")
        val otherInfo = pluginInfo("third", release("1.0.0"), pluginId = "other-plugin")
        setRepositories(repository("official", officialInfo), repository("third", thirdPartyInfo, otherInfo))

        val result = updateManager.pluginsMap

        assertEquals(setOf(PLUGIN_ID, "other-plugin"), result.keys)
        assertSame(officialInfo, result[PLUGIN_ID])
        assertSame(otherInfo, result["other-plugin"])
        assertEquals(2, updateManager.plugins.size)
        assertSame(officialInfo, updateManager.getPluginsMap()[PLUGIN_ID])
    }

    @Test
    fun `getPluginsMap should be empty without repositories`() {
        assertTrue(updateManager.pluginsMap.isEmpty())
    }

    @Test
    fun `findPluginInfo should prefer explicitly given marketplace`() {
        val officialInfo = pluginInfo("official", release("1.0.0"))
        val thirdPartyInfo = pluginInfo("third", release("1.0.0"))
        setRepositories(repository("official", officialInfo), repository("third", thirdPartyInfo))

        assertSame(thirdPartyInfo, updateManager.findPluginInfo(PLUGIN_ID, "third"))
    }

    @Test
    fun `findPluginInfo should return null if plugin is not in explicitly given marketplace`() {
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"))), repository("third"))

        assertNull(updateManager.findPluginInfo(PLUGIN_ID, "third"))
        assertNull(updateManager.findPluginInfo(PLUGIN_ID, "unknown"))
    }

    @Test
    fun `findPluginInfo should prefer the marketplace the plugin was installed from`() {
        val officialInfo = pluginInfo("official", release("1.0.0"))
        val thirdPartyInfo = pluginInfo("third", release("1.0.0"))
        setRepositories(repository("official", officialInfo), repository("third", thirdPartyInfo))
        entries[PLUGIN_ID] = PluginManagementEntry(PLUGIN_ID, sourceMarketplaceId = "third")

        assertSame(thirdPartyInfo, updateManager.findPluginInfo(PLUGIN_ID))
    }

    @Test
    fun `findPluginInfo should fall back to first marketplace if source marketplace does not list the plugin`() {
        val officialInfo = pluginInfo("official", release("1.0.0"))
        val thirdPartyInfo = pluginInfo("third", release("1.0.0"))
        setRepositories(repository("removed"), repository("official", officialInfo), repository("third", thirdPartyInfo))
        entries[PLUGIN_ID] = PluginManagementEntry(PLUGIN_ID, sourceMarketplaceId = "removed")

        assertSame(officialInfo, updateManager.findPluginInfo(PLUGIN_ID))

        entries[PLUGIN_ID]!!.sourceMarketplaceId = "gone-entirely"
        assertSame(officialInfo, updateManager.findPluginInfo(PLUGIN_ID))
    }

    @Test
    fun `findPluginInfo should return first match without source marketplace`() {
        val thirdPartyInfo = pluginInfo("third", release("1.0.0"))
        setRepositories(repository("official"), repository("third", thirdPartyInfo))

        assertSame(thirdPartyInfo, updateManager.findPluginInfo(PLUGIN_ID))
        assertNull(updateManager.findPluginInfo("unknown-plugin"))
    }

    @Test
    fun `getLastPluginRelease should return highest compatible version`() {
        setRepositories(
            repository(
                "official",
                pluginInfo(
                    "official",
                    release("1.0.0"),
                    release("1.10.0", requires = ">=1.0.0"),
                    release("1.2.0"),
                    release("3.0.0", requires = ">=3.0.0")
                )
            )
        )

        assertEquals("1.10.0", updateManager.getLastPluginRelease(PLUGIN_ID)?.version)
        assertNull(updateManager.getLastPluginRelease("unknown-plugin"))
    }

    @Test
    fun `getLastPluginRelease should return null if no version is compatible`() {
        setRepositories(repository("official", pluginInfo("official", release("3.0.0", requires = ">=3.0.0"))))

        assertNull(updateManager.getLastPluginRelease(PLUGIN_ID))
    }

    @Test
    fun `getLastRelease should ignore compatibility`() {
        val info = pluginInfo("official", release("1.0.0"), release("3.0.0", requires = ">=3.0.0"), release("1.10.0"))

        assertEquals("3.0.0", updateManager.getLastRelease(info)?.version)
        assertEquals("1.10.0", updateManager.getLastCompatibleRelease(info)?.version)
        assertNull(updateManager.getLastRelease(GameyfinPluginInfo()))
        assertNull(updateManager.getLastCompatibleRelease(GameyfinPluginInfo()))
    }

    @Test
    fun `isCompatible should delegate to plugin manager`() {
        assertTrue(updateManager.isCompatible(release("1.0.0", requires = ">=1.0.0")))
        assertFalse(updateManager.isCompatible(release("1.0.0", requires = ">=3.0.0")))
        verify { pluginManager.isCompatible(">=3.0.0") }
    }

    @Test
    fun `hasPluginUpdate should be false if plugin is not installed`() {
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"))))

        assertFalse(updateManager.hasPluginUpdate(PLUGIN_ID))
    }

    @Test
    fun `hasPluginUpdate should be true if a newer compatible version exists`() {
        setRepositories(
            repository(
                "official",
                pluginInfo("official", release("1.2.0"), release("1.10.0"), release("3.0.0", requires = ">=3.0.0"))
            )
        )
        preload(installedFile("1.2.0"), "1.2.0", enabled = true)

        assertTrue(updateManager.hasPluginUpdate(PLUGIN_ID))
    }

    @Test
    fun `hasPluginUpdate should be false if installed version is the latest compatible one`() {
        setRepositories(
            repository("official", pluginInfo("official", release("1.0.0"), release("3.0.0", requires = ">=3.0.0")))
        )
        preload(installedFile("1.0.0"), "1.0.0", enabled = true)

        assertFalse(updateManager.hasPluginUpdate(PLUGIN_ID))
    }

    @Test
    fun `hasPluginUpdate should be false if installed version is newer`() {
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"))))
        preload(localRoot.resolve("local.jar"), "1.5.0", enabled = true)

        assertFalse(updateManager.hasPluginUpdate(PLUGIN_ID))
    }

    @Test
    fun `hasPluginUpdate should be false if plugin is not listed in any marketplace`() {
        setRepositories(repository("official"))
        preload(localRoot.resolve("local.jar"), "1.0.0", enabled = true)

        assertFalse(updateManager.hasPluginUpdate(PLUGIN_ID))
    }

    // ========================================================================================
    // installPlugin - validation errors
    // ========================================================================================

    @Test
    fun `installPlugin should fail for unknown marketplace`() {
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"))))

        val e = assertFailsWith<IllegalArgumentException> { updateManager.installPlugin("unknown", PLUGIN_ID, null) }

        assertTrue(e.message!!.contains("Marketplace 'unknown' not found"))
        verify(exactly = 0) { fileDownloader.downloadFile(any()) }
    }

    @Test
    fun `installPlugin should fail for unknown plugin`() {
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"))))

        val e = assertFailsWith<IllegalArgumentException> {
            updateManager.installPlugin("official", "unknown-plugin", null)
        }

        assertTrue(e.message!!.contains("Plugin 'unknown-plugin' not found"))
        verify(exactly = 0) { fileDownloader.downloadFile(any()) }
    }

    @Test
    fun `installPlugin should fail for unknown version`() {
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"))))

        val e = assertFailsWith<IllegalArgumentException> {
            updateManager.installPlugin("official", PLUGIN_ID, "1.1.0")
        }

        assertTrue(e.message!!.contains("Version 1.1.0"))
        verify(exactly = 0) { fileDownloader.downloadFile(any()) }
    }

    @Test
    fun `installPlugin should fail for incompatible version`() {
        setRepositories(
            repository("official", pluginInfo("official", release("1.0.0"), release("3.0.0", requires = ">=3.0.0")))
        )

        val e = assertFailsWith<IllegalStateException> {
            updateManager.installPlugin("official", PLUGIN_ID, "3.0.0")
        }

        assertTrue(e.message!!.contains("requires Gameyfin '>=3.0.0'"))
        assertTrue(e.message!!.contains(SYSTEM_VERSION))
        verify(exactly = 0) { fileDownloader.downloadFile(any()) }
    }

    @Test
    fun `installPlugin should fail if no version is compatible`() {
        setRepositories(repository("official", pluginInfo("official", release("3.0.0", requires = ">=3.0.0"))))

        val e = assertFailsWith<IllegalStateException> { updateManager.installPlugin("official", PLUGIN_ID, null) }

        assertTrue(e.message!!.contains("No version"))
        verify(exactly = 0) { fileDownloader.downloadFile(any()) }
    }

    @Test
    fun `installPlugin should fail if the same version is already installed from the marketplace`() {
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"))))
        preload(installedFile("1.0.0"), "1.0.0", enabled = true, sourceMarketplaceId = "official")

        val e = assertFailsWith<IllegalStateException> { updateManager.installPlugin("official", PLUGIN_ID, "1.0.0") }

        assertTrue(e.message!!.contains("already installed"))
        verify(exactly = 0) { fileDownloader.downloadFile(any()) }
        verify(exactly = 0) { pluginManager.unloadPlugin(any()) }
    }

    @Test
    fun `installPlugin should fail if an equal local version exists`() {
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"))))
        val localPath = preload(localRoot.resolve("bundled.jar"), "1.0.0", enabled = true).pluginPath
        localPluginPath = localPath

        val e = assertFailsWith<IllegalStateException> { updateManager.installPlugin("official", PLUGIN_ID, null) }

        assertTrue(e.message!!.contains("already provided locally"))
        verify(exactly = 0) { fileDownloader.downloadFile(any()) }
    }

    @Test
    fun `installPlugin should fail if a newer local version exists`() {
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"), release("1.1.0"))))
        // Marketplace version 1.0.0 is loaded, a local 1.2.0 exists (e.g. bundled after a Gameyfin update)
        preload(installedFile("1.0.0"), "1.0.0", enabled = true, sourceMarketplaceId = "official")
        localPluginPath = writePluginFile(localRoot.resolve("bundled.jar"), PLUGIN_ID, "1.2.0")

        val e = assertFailsWith<IllegalStateException> { updateManager.installPlugin("official", PLUGIN_ID, "1.1.0") }

        assertTrue(e.message!!.contains("Version 1.2.0"))
        verify(exactly = 0) { fileDownloader.downloadFile(any()) }
    }

    @Test
    fun `installPlugin should allow newer version than local version`() {
        setRepositories(repository("official", pluginInfo("official", release("1.1.0"))))
        localPluginPath = preload(localRoot.resolve("bundled.jar"), "1.0.0", enabled = true).pluginPath

        updateManager.installPlugin("official", PLUGIN_ID, null)

        assertEquals("1.1.0", loaded[PLUGIN_ID]?.descriptor?.version)
    }

    @Test
    fun `installPlugin should ignore incompatible local version`() {
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"))))
        localPluginPath = writePluginFile(localRoot.resolve("bundled.jar"), PLUGIN_ID, "5.0.0")
        every { pluginManager.readPluginDescriptor(localPluginPath!!) } returns
                DefaultPluginDescriptor(PLUGIN_ID, "", "Plugin", "5.0.0", ">=3.0.0", "author", "MIT")

        updateManager.installPlugin("official", PLUGIN_ID, null)

        assertEquals("1.0.0", loaded[PLUGIN_ID]?.descriptor?.version)
    }

    @Test
    fun `installPlugin should ignore unreadable local version`() {
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"))))
        localPluginPath = localRoot.resolve("broken.jar")
        every { pluginManager.readPluginDescriptor(localPluginPath!!) } returns null

        updateManager.installPlugin("official", PLUGIN_ID, null)

        assertEquals("1.0.0", loaded[PLUGIN_ID]?.descriptor?.version)
    }

    // ========================================================================================
    // installPlugin - success
    // ========================================================================================

    @Test
    fun `installPlugin should install new plugin into installed plugins root`() {
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"), release("1.1.0"))))

        val state = updateManager.installPlugin("official", PLUGIN_ID, null)

        val target = installedFile("1.1.0")
        assertTrue(target.exists())
        assertEquals("$PLUGIN_ID:1.1.0", target.readText())
        assertEquals(target, loaded[PLUGIN_ID]?.pluginPath)
        assertEquals("official", entries[PLUGIN_ID]?.sourceMarketplaceId)
        // New third-party plugins are not enabled automatically
        assertEquals(PluginState.RESOLVED, state)
        assertNoDownloadLeftovers()
        verify(exactly = 1) { pluginManager.loadPluginOrThrow(target) }
        verify(exactly = 0) { pluginManager.stopPlugin(any()) }
        verify(exactly = 0) { pluginManager.unloadPlugin(any()) }
        verify(exactly = 0) { pluginManager.startPlugin(any()) }
    }

    @Test
    fun `installPlugin should download and verify the selected release`() {
        val requested = release("1.0.0")
        setRepositories(repository("official", pluginInfo("official", requested, release("1.1.0"))))

        updateManager.installPlugin("official", PLUGIN_ID, "1.0.0")

        verify(exactly = 1) { fileDownloader.downloadFile(URI(requested.url).toURL()) }
        verify(exactly = 1) {
            fileVerifier.verify(match { it.id == PLUGIN_ID && it.version == "1.0.0" && it.url == requested.url }, any())
        }
        assertTrue(installedFile("1.0.0").exists())
        assertFalse(installedFile("1.1.0").exists())
    }

    @Test
    fun `installPlugin should start new official plugin`() {
        newEntryTrustLevel = PluginTrustLevel.OFFICIAL
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"))))

        val state = updateManager.installPlugin("official", PLUGIN_ID, null)

        assertEquals(PluginState.STARTED, state)
        assertEquals(true, entries[PLUGIN_ID]?.enabled)
        verify(exactly = 0) { pluginManager.disablePlugin(any()) }
        verify(exactly = 1) { pluginManager.startPlugin(PLUGIN_ID) }
    }

    @Test
    fun `installPlugin should not start compatible-check failing plugin`() {
        newEntryTrustLevel = PluginTrustLevel.OFFICIAL
        every { pluginManager.isCompatible(any<PluginWrapper>()) } returns false
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"))))

        val state = updateManager.installPlugin("official", PLUGIN_ID, null)

        assertEquals(PluginState.RESOLVED, state)
        verify(exactly = 0) { pluginManager.startPlugin(any()) }
    }

    @Test
    fun `installPlugin should disable previously enabled third-party plugin on fresh install`() {
        // Entry exists from a previous installation, but the plugin is currently not loaded
        entries[PLUGIN_ID] = PluginManagementEntry(
            PLUGIN_ID, enabled = true, priority = 1, trustLevel = PluginTrustLevel.THIRD_PARTY,
            sourceMarketplaceId = "official"
        )
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"))))

        val state = updateManager.installPlugin("official", PLUGIN_ID, null)

        verify(exactly = 1) { pluginManager.disablePlugin(PLUGIN_ID) }
        assertEquals(false, entries[PLUGIN_ID]?.enabled)
        assertEquals(PluginState.DISABLED, state)
        verify(exactly = 0) { pluginManager.startPlugin(any()) }
    }

    @Test
    fun `installPlugin should update plugin from the same marketplace`() {
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"), release("1.1.0"))))
        val previous = installedFile("1.0.0")
        preload(previous, "1.0.0", enabled = true, sourceMarketplaceId = "official")

        val state = updateManager.installPlugin("official", PLUGIN_ID, null)

        val target = installedFile("1.1.0")
        assertEquals(PluginState.STARTED, state)
        assertFalse(previous.exists(), "Old marketplace version should have been deleted")
        assertTrue(target.exists())
        assertEquals(target, loaded[PLUGIN_ID]?.pluginPath)
        assertEquals("official", entries[PLUGIN_ID]?.sourceMarketplaceId)
        assertEquals(true, entries[PLUGIN_ID]?.enabled)
        assertNoDownloadLeftovers()
        verifyOrder {
            pluginManager.stopPlugin(PLUGIN_ID)
            pluginManager.unloadPlugin(PLUGIN_ID)
            pluginManager.loadPluginOrThrow(target)
            pluginManager.startPlugin(PLUGIN_ID)
        }
        verify(exactly = 0) { pluginManager.disablePlugin(any()) }
    }

    @Test
    fun `installPlugin should downgrade plugin`() {
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"), release("1.1.0"))))
        val previous = installedFile("1.1.0")
        preload(previous, "1.1.0", enabled = true, sourceMarketplaceId = "official")

        updateManager.installPlugin("official", PLUGIN_ID, "1.0.0")

        assertFalse(previous.exists())
        assertTrue(installedFile("1.0.0").exists())
        assertEquals("1.0.0", loaded[PLUGIN_ID]?.descriptor?.version)
    }

    @Test
    fun `installPlugin should keep local plugin file when updating a bundled plugin`() {
        setRepositories(repository("official", pluginInfo("official", release("1.1.0"))))
        val bundled = localRoot.resolve("bundled.jar")
        preload(bundled, "1.0.0", enabled = true, trustLevel = PluginTrustLevel.BUNDLED)
        localPluginPath = bundled

        updateManager.installPlugin("official", PLUGIN_ID, null)

        assertTrue(bundled.exists(), "Local plugin file must not be deleted")
        assertTrue(installedFile("1.1.0").exists())
        assertEquals(installedFile("1.1.0"), loaded[PLUGIN_ID]?.pluginPath)
        assertEquals("official", entries[PLUGIN_ID]?.sourceMarketplaceId)
    }

    @Test
    fun `installPlugin should disable third-party plugin when source marketplace changes`() {
        setRepositories(
            repository("official", pluginInfo("official", release("1.0.0"))),
            repository("third", pluginInfo("third", release("1.1.0")))
        )
        val previous = installedFile("1.0.0")
        preload(previous, "1.0.0", enabled = true, sourceMarketplaceId = "official")

        val state = updateManager.installPlugin("third", PLUGIN_ID, null)

        verify(exactly = 1) { pluginManager.disablePlugin(PLUGIN_ID) }
        assertEquals(false, entries[PLUGIN_ID]?.enabled)
        assertEquals("third", entries[PLUGIN_ID]?.sourceMarketplaceId)
        assertEquals(PluginState.DISABLED, state)
        assertFalse(previous.exists())
    }

    @Test
    fun `installPlugin should disable third-party plugin when replacing a local plugin`() {
        setRepositories(repository("third", pluginInfo("third", release("1.1.0"))))
        preload(localRoot.resolve("manual.jar"), "1.0.0", enabled = true)

        updateManager.installPlugin("third", PLUGIN_ID, null)

        verify(exactly = 1) { pluginManager.disablePlugin(PLUGIN_ID) }
        assertEquals(false, entries[PLUGIN_ID]?.enabled)
    }

    @Test
    fun `installPlugin should keep official plugin enabled when source marketplace changes`() {
        setRepositories(
            repository("official", pluginInfo("official", release("1.0.0"))),
            repository("third", pluginInfo("third", release("1.1.0")))
        )
        preload(
            installedFile("1.0.0"), "1.0.0", enabled = true, trustLevel = PluginTrustLevel.OFFICIAL,
            sourceMarketplaceId = "official"
        )

        val state = updateManager.installPlugin("third", PLUGIN_ID, null)

        verify(exactly = 0) { pluginManager.disablePlugin(any()) }
        assertEquals(PluginState.STARTED, state)
        assertEquals("third", entries[PLUGIN_ID]?.sourceMarketplaceId)
    }

    @Test
    fun `installPlugin should not start disabled plugin`() {
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"), release("1.1.0"))))
        preload(installedFile("1.0.0"), "1.0.0", enabled = false, sourceMarketplaceId = "official")

        val state = updateManager.installPlugin("official", PLUGIN_ID, null)

        assertEquals(PluginState.RESOLVED, state)
        verify(exactly = 0) { pluginManager.startPlugin(any()) }
        verify(exactly = 0) { pluginManager.disablePlugin(any()) }
    }

    @Test
    fun `installPlugin should sanitize file name`() {
        setRepositories(repository("official", pluginInfo("official", release("1.0.0+build.7"))))

        updateManager.installPlugin("official", PLUGIN_ID, null)

        val target = installedRoot.resolve("$PLUGIN_ID-1.0.0_build.7.jar")
        assertTrue(target.exists())
        assertEquals(target, loaded[PLUGIN_ID]?.pluginPath)
    }

    @Test
    fun `installPlugin should create installed plugins root if missing`() {
        installedRoot.deleteExisting()
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"))))

        updateManager.installPlugin("official", PLUGIN_ID, null)

        assertTrue(installedFile("1.0.0").exists())
    }

    @Test
    fun `installPlugin by id should use the marketplace listing the plugin`() {
        newEntryTrustLevel = PluginTrustLevel.OFFICIAL
        setRepositories(repository("official"), repository("third", pluginInfo("third", release("1.0.0"))))

        val result = updateManager.installPlugin(PLUGIN_ID, null)

        assertTrue(result)
        assertEquals("third", entries[PLUGIN_ID]?.sourceMarketplaceId)
    }

    @Test
    fun `installPlugin by id should return false if plugin is not started`() {
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"))))

        assertFalse(updateManager.installPlugin(PLUGIN_ID, "1.0.0"))
        assertTrue(installedFile("1.0.0").exists())
    }

    @Test
    fun `installPlugin by id should fail for unknown plugin`() {
        setRepositories(repository("official"))

        assertFailsWith<PluginRuntimeException> { updateManager.installPlugin(PLUGIN_ID, null) }
    }

    @Test
    fun `updatePlugin should fail if plugin is not installed`() {
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"))))

        assertFailsWith<PluginRuntimeException> { updateManager.updatePlugin(PLUGIN_ID, null) }
        verify(exactly = 0) { fileDownloader.downloadFile(any()) }
    }

    @Test
    fun `updatePlugin should install newer version`() {
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"), release("1.1.0"))))
        preload(installedFile("1.0.0"), "1.0.0", enabled = true, sourceMarketplaceId = "official")

        assertTrue(updateManager.updatePlugin(PLUGIN_ID, null))
        assertEquals("1.1.0", loaded[PLUGIN_ID]?.descriptor?.version)
    }

    // ========================================================================================
    // installPlugin - failures and rollback
    // ========================================================================================

    @Test
    fun `installPlugin should not touch installed plugin if verification fails`() {
        every { fileVerifier.verify(any(), any()) } throws VerifyException("checksum mismatch")
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"), release("1.1.0"))))
        val previous = installedFile("1.0.0")
        val wrapper = preload(previous, "1.0.0", enabled = true, sourceMarketplaceId = "official")

        assertFailsWith<VerifyException> { updateManager.installPlugin("official", PLUGIN_ID, null) }

        assertSame(wrapper, loaded[PLUGIN_ID])
        assertTrue(previous.exists())
        assertFalse(installedFile("1.1.0").exists())
        assertNoDownloadLeftovers()
        verify(exactly = 0) { pluginManager.stopPlugin(any()) }
        verify(exactly = 0) { pluginManager.unloadPlugin(any()) }
    }

    @Test
    fun `installPlugin should propagate download failures`() {
        every { fileDownloader.downloadFile(any()) } throws java.io.IOException("connection refused")
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"))))

        assertFailsWith<java.io.IOException> { updateManager.installPlugin("official", PLUGIN_ID, null) }

        assertFalse(installedFile("1.0.0").exists())
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun `installPlugin should roll back to previous version if loading fails`() {
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"), release("1.1.0"))))
        val previous = installedFile("1.0.0")
        preload(previous, "1.0.0", enabled = true, sourceMarketplaceId = "official")
        failingLoadPaths.add(installedFile("1.1.0"))

        assertFailsWith<PluginRuntimeException> { updateManager.installPlugin("official", PLUGIN_ID, null) }

        assertFalse(installedFile("1.1.0").exists(), "Failed version should have been removed")
        assertTrue(previous.exists())
        assertEquals(previous, loaded[PLUGIN_ID]?.pluginPath)
        assertEquals(PluginState.STARTED, loaded[PLUGIN_ID]?.pluginState)
        assertEquals("official", entries[PLUGIN_ID]?.sourceMarketplaceId)
        assertNoDownloadLeftovers()
        verify(exactly = 1) { pluginManager.loadPluginOrThrow(previous) }
    }

    @Test
    fun `installPlugin should roll back to local version if loading fails`() {
        setRepositories(repository("third", pluginInfo("third", release("1.1.0"))))
        val bundled = localRoot.resolve("bundled.jar")
        preload(bundled, "1.0.0", enabled = true)
        failingLoadPaths.add(installedFile("1.1.0"))

        assertFailsWith<PluginRuntimeException> { updateManager.installPlugin("third", PLUGIN_ID, null) }

        assertTrue(bundled.exists())
        assertEquals(bundled, loaded[PLUGIN_ID]?.pluginPath)
        assertNull(entries[PLUGIN_ID]?.sourceMarketplaceId)
        assertEquals(true, entries[PLUGIN_ID]?.enabled)
        verify(exactly = 0) { pluginManager.disablePlugin(any()) }
    }

    @Test
    fun `installPlugin should remove new plugin file if fresh install fails to load`() {
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"))))
        failingLoadPaths.add(installedFile("1.0.0"))

        assertFailsWith<PluginRuntimeException> { updateManager.installPlugin("official", PLUGIN_ID, null) }

        assertFalse(installedFile("1.0.0").exists())
        assertTrue(loaded.isEmpty())
        assertNoDownloadLeftovers()
    }

    @Test
    fun `installPlugin should roll back if downloaded plugin has a different ID`() {
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"), release("1.1.0"))))
        val previous = installedFile("1.0.0")
        preload(previous, "1.0.0", enabled = true, sourceMarketplaceId = "official")
        every { fileDownloader.downloadFile(any()) } answers {
            val file = Files.createTempFile(downloadSourceDir, "plugin-", ".jar.part")
            writePluginFile(file, "other-plugin", "1.1.0")
            downloadedFiles.add(file)
            file
        }

        val e = assertFailsWith<IllegalStateException> { updateManager.installPlugin("official", PLUGIN_ID, null) }

        assertTrue(e.message!!.contains("other-plugin"))
        assertFalse(installedFile("1.1.0").exists())
        assertEquals(previous, loaded[PLUGIN_ID]?.pluginPath)
        assertNull(loaded["other-plugin"], "Plugin loaded under the wrong ID should have been unloaded")
        verify(exactly = 1) { pluginManager.unloadPlugin("other-plugin") }
    }

    @Test
    fun `installPlugin should tolerate failing rollback`() {
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"), release("1.1.0"))))
        val previous = installedFile("1.0.0")
        preload(previous, "1.0.0", enabled = true, sourceMarketplaceId = "official")
        failingLoadPaths.add(installedFile("1.1.0"))
        failingLoadPaths.add(previous)

        val e = assertFailsWith<PluginRuntimeException> { updateManager.installPlugin("official", PLUGIN_ID, null) }

        // The original exception is propagated, not the one of the rollback
        assertTrue(e.message!!.contains("1.1.0"))
        assertNull(loaded[PLUGIN_ID])
        assertTrue(previous.exists())
    }

    @Test
    fun `installPlugin should restart previous version if unloading fails`() {
        setRepositories(repository("official", pluginInfo("official", release("1.0.0"), release("1.1.0"))))
        val previous = installedFile("1.0.0")
        val wrapper = preload(previous, "1.0.0", enabled = true, sourceMarketplaceId = "official")
        every { pluginManager.unloadPlugin(PLUGIN_ID) } returns false

        val e = assertFailsWith<IllegalStateException> { updateManager.installPlugin("official", PLUGIN_ID, null) }

        assertTrue(e.message!!.contains("Failed to unload"))
        assertSame(wrapper, loaded[PLUGIN_ID])
        assertEquals(PluginState.STARTED, wrapper.pluginState)
        assertTrue(previous.exists())
        assertFalse(installedFile("1.1.0").exists())
        assertNoDownloadLeftovers()
        verify(exactly = 0) { pluginManager.loadPluginOrThrow(any()) }
        verifyOrder {
            pluginManager.stopPlugin(PLUGIN_ID)
            pluginManager.unloadPlugin(PLUGIN_ID)
            pluginManager.startPlugin(PLUGIN_ID)
        }
    }

    // ========================================================================================
    // uninstall
    // ========================================================================================

    @Test
    fun `uninstall should fail if plugin is not installed`() {
        val e = assertFailsWith<IllegalArgumentException> { updateManager.uninstall(PLUGIN_ID) }

        assertTrue(e.message!!.contains("not installed"))
    }

    @Test
    fun `uninstall should fail for local plugins`() {
        val bundled = localRoot.resolve("bundled.jar")
        preload(bundled, "1.0.0", enabled = true)

        val e = assertFailsWith<IllegalStateException> { updateManager.uninstall(PLUGIN_ID) }

        assertTrue(e.message!!.contains("not installed via the marketplace"))
        assertTrue(bundled.exists())
        verify(exactly = 0) { pluginManager.deletePlugin(any()) }
    }

    @Test
    fun `uninstall should fail if deleting fails`() {
        val installed = installedFile("1.0.0")
        val wrapper = preload(installed, "1.0.0", enabled = true, sourceMarketplaceId = "official")
        wrapper.failedException = RuntimeException("file is locked")
        every { pluginManager.deletePlugin(PLUGIN_ID) } returns false

        val e = assertFailsWith<IllegalStateException> { updateManager.uninstall(PLUGIN_ID) }

        assertTrue(e.message!!.contains("Failed to uninstall"))
        assertTrue(e.message!!.contains("file is locked"))
        assertEquals("official", entries[PLUGIN_ID]?.sourceMarketplaceId)
    }

    @Test
    fun `uninstall should fail without detail message if deleting fails without exception`() {
        preload(installedFile("1.0.0"), "1.0.0", enabled = true, sourceMarketplaceId = "official")
        every { pluginManager.deletePlugin(PLUGIN_ID) } returns false

        val e = assertFailsWith<IllegalStateException> { updateManager.uninstall(PLUGIN_ID) }

        assertEquals("Failed to uninstall plugin '$PLUGIN_ID'", e.message)
    }

    @Test
    fun `uninstall should delete plugin and clear source marketplace`() {
        val installed = installedFile("1.0.0")
        preload(installed, "1.0.0", enabled = true, sourceMarketplaceId = "official")

        val state = updateManager.uninstall(PLUGIN_ID)

        assertNull(state)
        assertFalse(installed.exists())
        assertNull(loaded[PLUGIN_ID])
        assertNull(entries[PLUGIN_ID]?.sourceMarketplaceId)
        verify(exactly = 1) { pluginManager.deletePlugin(PLUGIN_ID) }
        verify(exactly = 0) { pluginManager.loadPluginOrThrow(any()) }
    }

    @Test
    fun `uninstall should work without management entry`() {
        val installed = installedFile("1.0.0")
        preload(installed, "1.0.0", enabled = true)
        entries.clear()

        assertNull(updateManager.uninstall(PLUGIN_ID))
        assertFalse(installed.exists())
        verify(exactly = 0) { pluginManagementRepository.save(any()) }
    }

    @Test
    fun `uninstall should fall back to local version`() {
        val bundled = writePluginFile(localRoot.resolve("bundled.jar"), PLUGIN_ID, "1.0.0")
        localPluginPath = bundled
        val installed = installedFile("1.1.0")
        preload(installed, "1.1.0", enabled = true, trustLevel = PluginTrustLevel.BUNDLED, sourceMarketplaceId = "official")

        val state = updateManager.uninstall(PLUGIN_ID)

        assertEquals(PluginState.STARTED, state)
        assertFalse(installed.exists())
        assertTrue(bundled.exists())
        assertEquals(bundled, loaded[PLUGIN_ID]?.pluginPath)
        assertNull(entries[PLUGIN_ID]?.sourceMarketplaceId)
        verifyOrder {
            pluginManager.deletePlugin(PLUGIN_ID)
            pluginManager.loadPluginOrThrow(bundled)
            pluginManager.startPlugin(PLUGIN_ID)
        }
    }

    @Test
    fun `uninstall should not start disabled local version`() {
        localPluginPath = writePluginFile(localRoot.resolve("bundled.jar"), PLUGIN_ID, "1.0.0")
        preload(installedFile("1.1.0"), "1.1.0", enabled = false, sourceMarketplaceId = "official")

        val state = updateManager.uninstall(PLUGIN_ID)

        assertEquals(PluginState.RESOLVED, state)
        verify(exactly = 0) { pluginManager.startPlugin(any()) }
    }

    @Test
    fun `uninstall should return null if local version fails to load`() {
        val bundled = writePluginFile(localRoot.resolve("bundled.jar"), PLUGIN_ID, "1.0.0")
        localPluginPath = bundled
        failingLoadPaths.add(bundled)
        val installed = installedFile("1.1.0")
        preload(installed, "1.1.0", enabled = true, sourceMarketplaceId = "official")

        val state = updateManager.uninstall(PLUGIN_ID)

        assertNull(state)
        assertFalse(installed.exists())
        assertNull(loaded[PLUGIN_ID])
        assertNull(entries[PLUGIN_ID]?.sourceMarketplaceId)
    }

    @Test
    fun `uninstallPlugin should delegate to uninstall`() {
        val installed = installedFile("1.0.0")
        preload(installed, "1.0.0", enabled = true, sourceMarketplaceId = "official")

        assertTrue(updateManager.uninstallPlugin(PLUGIN_ID))
        assertFalse(installed.exists())
    }

    @Test
    fun `uninstallPlugin should propagate errors`() {
        assertFailsWith<IllegalArgumentException> { updateManager.uninstallPlugin(PLUGIN_ID) }
    }
}
