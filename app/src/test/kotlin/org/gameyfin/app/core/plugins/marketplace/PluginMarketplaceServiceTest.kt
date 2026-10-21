package org.gameyfin.app.core.plugins.marketplace

import io.mockk.*
import org.gameyfin.app.core.plugins.PluginService
import org.gameyfin.app.core.plugins.dto.PluginDto
import org.gameyfin.app.core.plugins.management.GameyfinPluginManager
import org.gameyfin.app.core.plugins.management.PluginManagementEntry
import org.gameyfin.app.core.plugins.management.PluginManagementRepository
import org.gameyfin.app.core.plugins.management.PluginOrigin
import org.gameyfin.app.core.plugins.marketplace.PluginMarketplaceService.Companion.DEFAULT_OFFICIAL_MARKETPLACE_URL
import org.gameyfin.app.core.plugins.marketplace.PluginMarketplaceService.Companion.OFFICIAL_MARKETPLACE_ID
import org.gameyfin.app.core.plugins.marketplace.PluginMarketplaceService.Companion.OFFICIAL_MARKETPLACE_NAME
import org.gameyfin.app.core.plugins.marketplace.dto.MarketplacePluginStatus
import org.gameyfin.app.core.plugins.marketplace.dto.PluginMarketplaceCreateDto
import org.gameyfin.app.core.plugins.marketplace.dto.PluginMarketplaceUpdateDto
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.pf4j.DefaultPluginDescriptor
import org.pf4j.DefaultVersionManager
import org.pf4j.PluginState
import org.pf4j.PluginWrapper
import org.pf4j.update.PluginInfo.PluginRelease
import java.io.IOException
import java.net.URI
import java.time.Instant
import java.util.*
import kotlin.test.*

class PluginMarketplaceServiceTest {

    companion object {
        private const val OFFICIAL_INDEX_URL = "https://raw.githubusercontent.com/gameyfin/plugin-marketplace/HEAD/plugins.json"
        private const val THIRD_URL = "https://github.com/example/marketplace"
        private const val THIRD_INDEX_URL = "https://raw.githubusercontent.com/example/marketplace/HEAD/plugins.json"
        private const val SYSTEM_VERSION = "2.0.0"
    }

    private lateinit var updateManager: GameyfinUpdateManager
    private lateinit var pluginManager: GameyfinPluginManager
    private lateinit var pluginService: PluginService
    private lateinit var marketplaceRepository: PluginMarketplaceRepository
    private lateinit var pluginManagementRepository: PluginManagementRepository
    private lateinit var httpClient: MarketplaceHttpClient

    /** Fake database of the marketplace repository */
    private val marketplaces = linkedMapOf<String, PluginMarketplaceEntry>()

    /** Repositories currently registered in the (mocked) update manager */
    private var activeRepositories = listOf<GameyfinUpdateRepository>()

    /** Plugins returned by repositories created via [GameyfinUpdateManager.createRepository], by index URL */
    private val pluginsByIndexUrl = mutableMapOf<URI, Map<String, GameyfinPluginInfo>>()
    private val failingIndexUrls = mutableSetOf<URI>()
    private val createdRepositories = mutableListOf<GameyfinUpdateRepository>()

    private val installedPlugins = mutableMapOf<String, PluginWrapper>()
    private val managementEntries = mutableMapOf<String, PluginManagementEntry>()

    private val versionManager = DefaultVersionManager()

    @BeforeEach
    fun setup() {
        marketplaceRepository = mockk()
        every { marketplaceRepository.findAll() } answers { marketplaces.values.toList() }
        every { marketplaceRepository.findById(any()) } answers { Optional.ofNullable(marketplaces[firstArg()]) }
        every { marketplaceRepository.save(any<PluginMarketplaceEntry>()) } answers {
            firstArg<PluginMarketplaceEntry>().also { marketplaces[it.id] = it }
        }
        every { marketplaceRepository.delete(any()) } answers { marketplaces.remove(firstArg<PluginMarketplaceEntry>().id) }
        every { marketplaceRepository.existsByIndexUrl(any()) } answers {
            marketplaces.values.any { it.indexUrl == firstArg<String>() }
        }

        updateManager = mockk()
        every { updateManager.getMarketplaceRepositories() } answers { activeRepositories }
        every { updateManager.getMarketplaceRepository(any()) } answers {
            activeRepositories.find { it.id == firstArg<String>() }
        }
        every { updateManager.setMarketplaceRepositories(any()) } answers { activeRepositories = firstArg() }
        every { updateManager.createRepository(any(), any()) } answers {
            val id = firstArg<String>()
            val indexUrl = secondArg<URI>()
            repository(id, indexUrl, pluginsByIndexUrl[indexUrl].orEmpty()).also {
                if (indexUrl in failingIndexUrls) {
                    every { it.fetchOrThrow() } throws IOException("Failed to fetch $indexUrl")
                }
                createdRepositories.add(it)
            }
        }
        every { updateManager.isCompatible(any()) } answers { isCompatible(firstArg<PluginRelease>().requires) }
        every { updateManager.refresh() } just Runs

        pluginManager = mockk()
        every { pluginManager.versionManager } returns versionManager
        every { pluginManager.getPlugin(any()) } answers { installedPlugins[firstArg()] }
        every { pluginManager.getPluginOrigin(any()) } returns PluginOrigin.LOCAL

        pluginManagementRepository = mockk()
        every { pluginManagementRepository.findById(any()) } answers {
            Optional.ofNullable(managementEntries[firstArg()])
        }

        pluginService = mockk(relaxed = true)

        httpClient = mockk()
        every { httpClient.allowInsecureUrls } returns false
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    // ========================================================================================
    // Helpers
    // ========================================================================================

    private fun service(officialUrl: String = DEFAULT_OFFICIAL_MARKETPLACE_URL) = PluginMarketplaceService(
        updateManager,
        pluginManager,
        pluginService,
        marketplaceRepository,
        pluginManagementRepository,
        httpClient,
        officialUrl
    )

    private fun isCompatible(requires: String?): Boolean {
        val trimmed = requires?.trim().orEmpty()
        return trimmed.isEmpty() || trimmed == "*" || versionManager.checkVersionConstraint(SYSTEM_VERSION, trimmed)
    }

    private fun repository(
        id: String,
        indexUrl: URI = URI("https://example.com/$id/plugins.json"),
        plugins: Map<String, GameyfinPluginInfo> = emptyMap()
    ): GameyfinUpdateRepository {
        val repository = mockk<GameyfinUpdateRepository>()
        every { repository.id } returns id
        every { repository.indexUrl } returns indexUrl
        every { repository.getGameyfinPlugins() } returns plugins
        every { repository.fetchOrThrow() } returns plugins
        every { repository.lastFetchedAt } returns null
        every { repository.lastError } returns null
        return repository
    }

    private fun addMarketplaceEntry(
        id: String,
        name: String = id,
        indexUrl: String = "https://example.com/$id/plugins.json",
        enabled: Boolean = true,
        official: Boolean = false,
        createdAt: Instant = Instant.parse("2024-01-01T00:00:00Z")
    ): PluginMarketplaceEntry {
        val entry = PluginMarketplaceEntry(
            id = id,
            name = name,
            url = indexUrl,
            indexUrl = indexUrl,
            enabled = enabled,
            official = official,
            createdAt = createdAt
        )
        marketplaces[id] = entry
        return entry
    }

    private fun officialEntry() = addMarketplaceEntry(
        OFFICIAL_MARKETPLACE_ID,
        name = OFFICIAL_MARKETPLACE_NAME,
        indexUrl = OFFICIAL_INDEX_URL,
        official = true
    ).also { it.url = DEFAULT_OFFICIAL_MARKETPLACE_URL }

    private fun release(
        version: String,
        requires: String? = "*",
        date: Date? = Date(0),
        changelog: String? = null
    ): GameyfinPluginRelease {
        return GameyfinPluginRelease().apply {
            this.version = version
            this.requires = requires
            this.date = date
            this.url = "https://example.com/plugin-$version.jar"
            this.changelog = changelog
        }
    }

    private fun pluginInfo(
        id: String,
        repositoryId: String,
        vararg releases: PluginRelease,
        name: String? = "Plugin $id",
        logoUrl: String? = null
    ): GameyfinPluginInfo {
        return GameyfinPluginInfo().apply {
            this.id = id
            this.name = name
            this.repositoryId = repositoryId
            this.releases = releases.toList()
            this.logoUrl = logoUrl
        }
    }

    private fun install(pluginId: String, version: String) {
        val wrapper = mockk<PluginWrapper>()
        every { wrapper.descriptor } returns DefaultPluginDescriptor(pluginId, "", "Plugin", version, "*", "a", "MIT")
        every { wrapper.pluginState } returns PluginState.STARTED
        installedPlugins[pluginId] = wrapper
    }

    // ========================================================================================
    // initialize
    // ========================================================================================

    @Test
    fun `initialize should seed official marketplace`() {
        service().initialize()

        val entry = marketplaces[OFFICIAL_MARKETPLACE_ID]
        assertNotNull(entry)
        assertEquals(OFFICIAL_MARKETPLACE_NAME, entry.name)
        assertEquals(DEFAULT_OFFICIAL_MARKETPLACE_URL, entry.url)
        assertEquals(OFFICIAL_INDEX_URL, entry.indexUrl)
        assertTrue(entry.official)
        assertTrue(entry.enabled)
        assertEquals(listOf(OFFICIAL_MARKETPLACE_ID), activeRepositories.map { it.id })
        assertEquals(URI(OFFICIAL_INDEX_URL), activeRepositories.single().indexUrl)
    }

    @Test
    fun `initialize should use configured official marketplace URL`() {
        service("https://example.com/my-marketplace").initialize()

        val entry = marketplaces.getValue(OFFICIAL_MARKETPLACE_ID)
        assertEquals("https://example.com/my-marketplace", entry.url)
        assertEquals("https://example.com/my-marketplace/plugins.json", entry.indexUrl)
    }

    @Test
    fun `initialize should update official marketplace if URL changed`() {
        val existing = officialEntry()
        existing.enabled = false

        service("https://github.com/other/marketplace").initialize()

        val entry = marketplaces.getValue(OFFICIAL_MARKETPLACE_ID)
        assertSame(existing, entry)
        assertEquals("https://github.com/other/marketplace", entry.url)
        assertEquals("https://raw.githubusercontent.com/other/marketplace/HEAD/plugins.json", entry.indexUrl)
        // User settings are kept
        assertFalse(entry.enabled)
        verify(exactly = 1) { marketplaceRepository.save(existing) }
        assertTrue(activeRepositories.isEmpty())
    }

    @Test
    fun `initialize should update official marketplace if only index URL is outdated`() {
        val existing = officialEntry()
        existing.indexUrl = "https://example.com/outdated.json"

        service().initialize()

        assertEquals(OFFICIAL_INDEX_URL, existing.indexUrl)
        verify(exactly = 1) { marketplaceRepository.save(existing) }
    }

    @Test
    fun `initialize should not save unchanged official marketplace`() {
        officialEntry()

        service().initialize()

        verify(exactly = 0) { marketplaceRepository.save(any()) }
        assertEquals(listOf(OFFICIAL_MARKETPLACE_ID), activeRepositories.map { it.id })
    }

    @Test
    fun `initialize should tolerate invalid official marketplace URL`() {
        addMarketplaceEntry("third", indexUrl = THIRD_INDEX_URL)

        service("ftp://example.com/marketplace").initialize()

        assertNull(marketplaces[OFFICIAL_MARKETPLACE_ID])
        verify(exactly = 0) { marketplaceRepository.save(any()) }
        // Repositories are synchronized anyway
        assertEquals(listOf("third"), activeRepositories.map { it.id })
    }

    @Test
    fun `initialize should tolerate database errors while seeding`() {
        every { marketplaceRepository.findById(OFFICIAL_MARKETPLACE_ID) } throws RuntimeException("DB down")
        addMarketplaceEntry("third", indexUrl = THIRD_INDEX_URL)

        service().initialize()

        assertEquals(listOf("third"), activeRepositories.map { it.id })
    }

    // ========================================================================================
    // getMarketplaces
    // ========================================================================================

    @Test
    fun `getMarketplaces should return official marketplace first and others by creation date`() {
        addMarketplaceEntry("newer", createdAt = Instant.parse("2024-02-01T00:00:00Z"))
        addMarketplaceEntry("older", createdAt = Instant.parse("2023-01-01T00:00:00Z"))
        officialEntry()

        val result = service().getMarketplaces()

        assertEquals(listOf(OFFICIAL_MARKETPLACE_ID, "older", "newer"), result.map { it.id })
    }

    @Test
    fun `getMarketplaces should map entries and repository state`() {
        val entry = addMarketplaceEntry("third", name = "Third", indexUrl = THIRD_INDEX_URL)
        entry.url = THIRD_URL
        addMarketplaceEntry("disabled", enabled = false)
        addMarketplaceEntry("not-loaded")
        val fetchedAt = Instant.parse("2024-03-01T12:00:00Z")
        val repository = repository(
            "third",
            plugins = mapOf("a" to pluginInfo("a", "third"), "b" to pluginInfo("b", "third"))
        )
        every { repository.lastFetchedAt } returns fetchedAt
        every { repository.lastError } returns "Some error"
        // A repository registered for a disabled marketplace is ignored for the plugin count
        activeRepositories = listOf(repository, repository("disabled", plugins = mapOf("a" to pluginInfo("a", "x"))))

        val result = service().getMarketplaces().associateBy { it.id }

        val third = result.getValue("third")
        assertEquals("Third", third.name)
        assertEquals(THIRD_URL, third.url)
        assertEquals(THIRD_INDEX_URL, third.indexUrl)
        assertTrue(third.enabled)
        assertFalse(third.official)
        assertEquals(2, third.pluginCount)
        assertEquals(fetchedAt, third.lastFetchedAt)
        assertEquals("Some error", third.lastError)

        assertEquals(0, result.getValue("disabled").pluginCount)
        assertFalse(result.getValue("disabled").enabled)
        assertEquals(0, result.getValue("not-loaded").pluginCount)
        assertNull(result.getValue("not-loaded").lastFetchedAt)
    }

    // ========================================================================================
    // addMarketplace
    // ========================================================================================

    @Test
    fun `addMarketplace should save and sync marketplace if index can be loaded`() {
        officialEntry()
        pluginsByIndexUrl[URI(THIRD_INDEX_URL)] = mapOf("a" to pluginInfo("a", "x"))
        val service = service()

        val result = service.addMarketplace(PluginMarketplaceCreateDto("  Third party  ", "  $THIRD_URL  "))

        val saved = marketplaces.getValue(result.id)
        assertEquals("Third party", saved.name)
        assertEquals(THIRD_URL, saved.url)
        assertEquals(THIRD_INDEX_URL, saved.indexUrl)
        assertTrue(saved.enabled)
        assertFalse(saved.official)
        // The ID is a random UUID
        UUID.fromString(result.id)

        assertEquals("Third party", result.name)
        assertEquals(THIRD_INDEX_URL, result.indexUrl)
        assertEquals(1, result.pluginCount)

        verify(exactly = 1) { createdRepositories.first().fetchOrThrow() }
        assertEquals(result.id, createdRepositories.first().id)
        assertEquals(listOf(OFFICIAL_MARKETPLACE_ID, result.id), activeRepositories.map { it.id })
    }

    @Test
    fun `addMarketplace should accept direct index URLs`() {
        val result = service().addMarketplace(PluginMarketplaceCreateDto("Direct", "https://example.com/index.json"))

        assertEquals("https://example.com/index.json", result.indexUrl)
        assertEquals("https://example.com/index.json", result.url)
    }

    @Test
    fun `addMarketplace should reject empty name`() {
        assertFailsWith<IllegalArgumentException> {
            service().addMarketplace(PluginMarketplaceCreateDto("   ", THIRD_URL))
        }
        assertTrue(marketplaces.isEmpty())
        verify(exactly = 0) { updateManager.createRepository(any(), any()) }
    }

    @Test
    fun `addMarketplace should reject too long name`() {
        val e = assertFailsWith<IllegalArgumentException> {
            service().addMarketplace(PluginMarketplaceCreateDto("x".repeat(256), THIRD_URL))
        }
        assertTrue(e.message!!.contains("255"))
        assertTrue(marketplaces.isEmpty())
    }

    @Test
    fun `addMarketplace should accept name with maximum length`() {
        val result = service().addMarketplace(PluginMarketplaceCreateDto("x".repeat(255), THIRD_URL))

        assertEquals(255, result.name.length)
    }

    @Test
    fun `addMarketplace should reject invalid URLs`() {
        val service = service()

        listOf("", "ftp://example.com/plugins.json", "http://example.com/plugins.json", "https://github.com/only-owner")
            .forEach { url ->
                assertFailsWith<IllegalArgumentException>("URL '$url' should be rejected") {
                    service.addMarketplace(PluginMarketplaceCreateDto("Name", url))
                }
            }

        assertTrue(marketplaces.isEmpty())
        verify(exactly = 0) { updateManager.createRepository(any(), any()) }
    }

    @Test
    fun `addMarketplace should accept insecure URLs if allowed`() {
        every { httpClient.allowInsecureUrls } returns true

        val result = service().addMarketplace(PluginMarketplaceCreateDto("Local", "http://localhost:8080/plugins.json"))

        assertEquals("http://localhost:8080/plugins.json", result.indexUrl)
    }

    @Test
    fun `addMarketplace should reject duplicate URLs`() {
        addMarketplaceEntry("third", indexUrl = THIRD_INDEX_URL)

        // Different input resolving to the same index URL
        val e = assertFailsWith<IllegalArgumentException> {
            service().addMarketplace(PluginMarketplaceCreateDto("Name", "github.com/example/marketplace"))
        }

        assertTrue(e.message!!.contains("already exists"))
        assertEquals(1, marketplaces.size)
        verify(exactly = 0) { updateManager.createRepository(any(), any()) }
    }

    @Test
    fun `addMarketplace should not save marketplace if index can't be loaded`() {
        failingIndexUrls.add(URI(THIRD_INDEX_URL))

        assertFailsWith<IOException> {
            service().addMarketplace(PluginMarketplaceCreateDto("Third", THIRD_URL))
        }

        assertTrue(marketplaces.isEmpty())
        verify(exactly = 0) { marketplaceRepository.save(any()) }
        verify(exactly = 0) { updateManager.setMarketplaceRepositories(any()) }
    }

    // ========================================================================================
    // updateMarketplace
    // ========================================================================================

    @Test
    fun `updateMarketplace should fail for unknown marketplace`() {
        assertFailsWith<IllegalArgumentException> {
            service().updateMarketplace("unknown", PluginMarketplaceUpdateDto(name = "x"))
        }
    }

    @Test
    fun `updateMarketplace should rename marketplace`() {
        val entry = addMarketplaceEntry("third", name = "Old")

        val result = service().updateMarketplace("third", PluginMarketplaceUpdateDto(name = "  New  "))

        assertEquals("New", entry.name)
        assertEquals("New", result.name)
        assertTrue(entry.enabled)
        verify(exactly = 1) { marketplaceRepository.save(entry) }
        verify(exactly = 1) { updateManager.setMarketplaceRepositories(any()) }
    }

    @Test
    fun `updateMarketplace should validate name`() {
        val entry = addMarketplaceEntry("third", name = "Old")

        assertFailsWith<IllegalArgumentException> {
            service().updateMarketplace("third", PluginMarketplaceUpdateDto(name = " "))
        }

        assertEquals("Old", entry.name)
        verify(exactly = 0) { marketplaceRepository.save(any()) }
    }

    @Test
    fun `updateMarketplace should not allow renaming official marketplace`() {
        val entry = officialEntry()

        val e = assertFailsWith<IllegalArgumentException> {
            service().updateMarketplace(OFFICIAL_MARKETPLACE_ID, PluginMarketplaceUpdateDto(name = "Mine"))
        }

        assertTrue(e.message!!.contains("can't be renamed"))
        assertEquals(OFFICIAL_MARKETPLACE_NAME, entry.name)
        verify(exactly = 0) { marketplaceRepository.save(any()) }
    }

    @Test
    fun `updateMarketplace should allow disabling and enabling official marketplace`() {
        val entry = officialEntry()
        val service = service()

        val disabled = service.updateMarketplace(OFFICIAL_MARKETPLACE_ID, PluginMarketplaceUpdateDto(enabled = false))

        assertFalse(entry.enabled)
        assertFalse(disabled.enabled)
        assertEquals(0, disabled.pluginCount)
        assertTrue(activeRepositories.isEmpty())

        val enabled = service.updateMarketplace(OFFICIAL_MARKETPLACE_ID, PluginMarketplaceUpdateDto(enabled = true))

        assertTrue(entry.enabled)
        assertTrue(enabled.enabled)
        assertEquals(listOf(OFFICIAL_MARKETPLACE_ID), activeRepositories.map { it.id })
    }

    @Test
    fun `updateMarketplace should update name and enabled state at once`() {
        val entry = addMarketplaceEntry("third", name = "Old")

        service().updateMarketplace("third", PluginMarketplaceUpdateDto(name = "New", enabled = false))

        assertEquals("New", entry.name)
        assertFalse(entry.enabled)
    }

    // ========================================================================================
    // removeMarketplace
    // ========================================================================================

    @Test
    fun `removeMarketplace should fail for unknown marketplace`() {
        assertFailsWith<IllegalArgumentException> { service().removeMarketplace("unknown") }
        verify(exactly = 0) { marketplaceRepository.delete(any()) }
    }

    @Test
    fun `removeMarketplace should not allow removing official marketplace`() {
        officialEntry()

        val e = assertFailsWith<IllegalArgumentException> { service().removeMarketplace(OFFICIAL_MARKETPLACE_ID) }

        assertTrue(e.message!!.contains("can't be removed"))
        assertNotNull(marketplaces[OFFICIAL_MARKETPLACE_ID])
        verify(exactly = 0) { marketplaceRepository.delete(any()) }
    }

    @Test
    fun `removeMarketplace should delete marketplace and sync repositories`() {
        officialEntry()
        val entry = addMarketplaceEntry("third")
        val service = service()
        service.syncRepositories()
        assertEquals(listOf(OFFICIAL_MARKETPLACE_ID, "third"), activeRepositories.map { it.id })

        service.removeMarketplace("third")

        verify(exactly = 1) { marketplaceRepository.delete(entry) }
        assertNull(marketplaces["third"])
        assertEquals(listOf(OFFICIAL_MARKETPLACE_ID), activeRepositories.map { it.id })
    }

    // ========================================================================================
    // syncRepositories
    // ========================================================================================

    @Test
    fun `syncRepositories should register enabled marketplaces with official first`() {
        addMarketplaceEntry("b", createdAt = Instant.parse("2024-02-01T00:00:00Z"))
        addMarketplaceEntry("a", createdAt = Instant.parse("2024-01-01T00:00:00Z"))
        addMarketplaceEntry("disabled", enabled = false, createdAt = Instant.parse("2023-01-01T00:00:00Z"))
        officialEntry()

        service().syncRepositories()

        assertEquals(listOf(OFFICIAL_MARKETPLACE_ID, "a", "b"), activeRepositories.map { it.id })
        assertEquals(URI("https://example.com/a/plugins.json"), activeRepositories[1].indexUrl)
    }

    @Test
    fun `syncRepositories should reuse unchanged repositories`() {
        officialEntry()
        val changed = addMarketplaceEntry("changed")
        val existingOfficial = repository(OFFICIAL_MARKETPLACE_ID, URI(OFFICIAL_INDEX_URL))
        val existingChanged = repository("changed", URI(changed.indexUrl))
        activeRepositories = listOf(existingOfficial, existingChanged)
        changed.indexUrl = "https://example.com/new/plugins.json"

        service().syncRepositories()

        assertSame(existingOfficial, activeRepositories[0])
        assertNotSame(existingChanged, activeRepositories[1])
        assertEquals(URI("https://example.com/new/plugins.json"), activeRepositories[1].indexUrl)
        verify(exactly = 0) { updateManager.createRepository(OFFICIAL_MARKETPLACE_ID, any()) }
        verify(exactly = 1) { updateManager.createRepository("changed", URI("https://example.com/new/plugins.json")) }
    }

    @Test
    fun `syncRepositories should remove repositories of deleted marketplaces`() {
        officialEntry()
        activeRepositories = listOf(repository(OFFICIAL_MARKETPLACE_ID, URI(OFFICIAL_INDEX_URL)), repository("gone"))

        service().syncRepositories()

        assertEquals(listOf(OFFICIAL_MARKETPLACE_ID), activeRepositories.map { it.id })
    }

    @Test
    fun `syncRepositories should skip marketplaces with invalid URL`() {
        officialEntry()
        addMarketplaceEntry("invalid", indexUrl = "https://example.com/with spaces/plugins.json")
        addMarketplaceEntry("valid", createdAt = Instant.parse("2025-01-01T00:00:00Z"))

        service().syncRepositories()

        assertEquals(listOf(OFFICIAL_MARKETPLACE_ID, "valid"), activeRepositories.map { it.id })
    }

    @Test
    fun `syncRepositories should clear repositories without marketplaces`() {
        activeRepositories = listOf(repository("gone"))

        service().syncRepositories()

        assertTrue(activeRepositories.isEmpty())
    }

    // ========================================================================================
    // refresh
    // ========================================================================================

    @Test
    fun `refresh should refresh update manager and return plugins`() {
        officialEntry()
        activeRepositories = listOf(
            repository(OFFICIAL_MARKETPLACE_ID, plugins = mapOf("a" to pluginInfo("a", OFFICIAL_MARKETPLACE_ID, release("1.0.0"))))
        )

        val result = service().refresh()

        verify(exactly = 1) { updateManager.refresh() }
        assertEquals(listOf("a"), result.map { it.id })
    }

    // ========================================================================================
    // getPlugins
    // ========================================================================================

    private fun setupPlugins(vararg plugins: GameyfinPluginInfo) {
        officialEntry()
        activeRepositories = listOf(repository(OFFICIAL_MARKETPLACE_ID, plugins = plugins.associateBy { it.id }))
    }

    private fun defaultReleases() = arrayOf(
        release("1.0.0"),
        release("1.10.0", requires = ">=1.0.0"),
        release("1.2.0"),
        release("3.0.0", requires = ">=3.0.0")
    )

    @Test
    fun `getPlugins should sort releases by version descending`() {
        setupPlugins(pluginInfo("p", OFFICIAL_MARKETPLACE_ID, *defaultReleases()))

        val plugin = service().getPlugins().single()

        assertEquals(listOf("3.0.0", "1.10.0", "1.2.0", "1.0.0"), plugin.releases.map { it.version })
        assertEquals("3.0.0", plugin.latestVersion)
        assertEquals("1.10.0", plugin.latestCompatibleVersion)
        assertEquals(listOf(false, true, true, true), plugin.releases.map { it.compatible })
    }

    @Test
    fun `getPlugins should report NOT_INSTALLED for compatible plugins which are not installed`() {
        setupPlugins(pluginInfo("p", OFFICIAL_MARKETPLACE_ID, *defaultReleases()))

        val plugin = service().getPlugins().single()

        assertEquals(MarketplacePluginStatus.NOT_INSTALLED, plugin.status)
        assertNull(plugin.installedVersion)
        assertNull(plugin.installedOrigin)
        assertNull(plugin.installedFromMarketplaceId)
        verify(exactly = 0) { pluginManager.getPluginOrigin(any()) }
    }

    @Test
    fun `getPlugins should report INCOMPATIBLE if no release is compatible`() {
        setupPlugins(
            pluginInfo("p", OFFICIAL_MARKETPLACE_ID, release("3.0.0", requires = ">=3.0.0")),
            pluginInfo("empty", OFFICIAL_MARKETPLACE_ID)
        )

        val plugins = service().getPlugins().associateBy { it.id }

        assertEquals(MarketplacePluginStatus.INCOMPATIBLE, plugins.getValue("p").status)
        assertEquals("3.0.0", plugins.getValue("p").latestVersion)
        assertNull(plugins.getValue("p").latestCompatibleVersion)
        assertEquals(MarketplacePluginStatus.INCOMPATIBLE, plugins.getValue("empty").status)
        assertTrue(plugins.getValue("empty").releases.isEmpty())
        assertNull(plugins.getValue("empty").latestVersion)
    }

    @Test
    fun `getPlugins should report UPDATE_AVAILABLE if a newer compatible release exists`() {
        setupPlugins(pluginInfo("p", OFFICIAL_MARKETPLACE_ID, *defaultReleases()))
        install("p", "1.2.0")
        managementEntries["p"] = PluginManagementEntry("p", sourceMarketplaceId = OFFICIAL_MARKETPLACE_ID)
        every { pluginManager.getPluginOrigin("p") } returns PluginOrigin.MARKETPLACE

        val plugin = service().getPlugins().single()

        assertEquals(MarketplacePluginStatus.UPDATE_AVAILABLE, plugin.status)
        assertEquals("1.2.0", plugin.installedVersion)
        assertEquals(PluginOrigin.MARKETPLACE, plugin.installedOrigin)
        assertEquals(OFFICIAL_MARKETPLACE_ID, plugin.installedFromMarketplaceId)
    }

    @Test
    fun `getPlugins should report INSTALLED if latest compatible release is installed`() {
        setupPlugins(pluginInfo("p", OFFICIAL_MARKETPLACE_ID, *defaultReleases()))
        install("p", "1.10.0")

        val plugin = service().getPlugins().single()

        assertEquals(MarketplacePluginStatus.INSTALLED, plugin.status)
        assertEquals("1.10.0", plugin.installedVersion)
        assertEquals(PluginOrigin.LOCAL, plugin.installedOrigin)
        assertNull(plugin.installedFromMarketplaceId)
    }

    @Test
    fun `getPlugins should report INSTALLED if a newer version is installed`() {
        setupPlugins(pluginInfo("p", OFFICIAL_MARKETPLACE_ID, release("1.0.0")))
        install("p", "1.5.0")

        assertEquals(MarketplacePluginStatus.INSTALLED, service().getPlugins().single().status)
    }

    @Test
    fun `getPlugins should report INSTALLED if installed and no release is compatible`() {
        setupPlugins(pluginInfo("p", OFFICIAL_MARKETPLACE_ID, release("3.0.0", requires = ">=3.0.0")))
        install("p", "1.0.0")

        assertEquals(MarketplacePluginStatus.INSTALLED, service().getPlugins().single().status)
    }

    @Test
    fun `getPlugins should map plugin metadata`() {
        val info = pluginInfo("p", OFFICIAL_MARKETPLACE_ID, release("1.0.0"), logoUrl = "https://example.com/logo.png")
            .apply {
                shortDescription = "Short"
                description = "Long description"
                provider = "Author"
                license = "MIT"
                projectUrl = "https://example.com/p"
                types = listOf("GameMetadataProvider")
                tags = listOf("metadata", "igdb")
            }
        setupPlugins(info)

        val plugin = service().getPlugins().single()

        assertEquals("p", plugin.id)
        assertEquals(OFFICIAL_MARKETPLACE_ID, plugin.marketplaceId)
        assertEquals(OFFICIAL_MARKETPLACE_NAME, plugin.marketplaceName)
        assertTrue(plugin.officialMarketplace)
        assertEquals("Plugin p", plugin.name)
        assertEquals("Short", plugin.shortDescription)
        assertEquals("Long description", plugin.description)
        assertEquals("Author", plugin.author)
        assertEquals("MIT", plugin.license)
        assertEquals("https://example.com/p", plugin.url)
        assertTrue(plugin.hasLogo)
        assertEquals(listOf("GameMetadataProvider"), plugin.types)
        assertEquals(listOf("metadata", "igdb"), plugin.tags)
    }

    @Test
    fun `getPlugins should fall back to plugin ID as name and report missing logo`() {
        setupPlugins(pluginInfo("p", OFFICIAL_MARKETPLACE_ID, release("1.0.0"), name = null))

        val plugin = service().getPlugins().single()

        assertEquals("p", plugin.name)
        assertFalse(plugin.hasLogo)
        assertNull(plugin.author)
        assertNull(plugin.url)
    }

    @Test
    fun `getPlugins should map release metadata`() {
        val plainRelease = PluginRelease().apply {
            version = "0.9.0"
            requires = ""
            date = null
            url = "https://example.com/p-0.9.0.jar"
        }
        setupPlugins(
            pluginInfo(
                "p",
                OFFICIAL_MARKETPLACE_ID,
                release("1.0.0", requires = "*", date = Date(0), changelog = "Initial"),
                release("1.1.0", requires = ">=1.0.0", date = Date(1_700_000_000_000)),
                release("1.2.0", requires = "  ", date = null),
                plainRelease
            )
        )

        val releases = service().getPlugins().single().releases.associateBy { it.version }

        assertNull(releases.getValue("1.0.0").date)
        assertNull(releases.getValue("1.0.0").requires)
        assertEquals("Initial", releases.getValue("1.0.0").changelog)
        assertTrue(releases.getValue("1.0.0").compatible)

        assertEquals(Instant.ofEpochMilli(1_700_000_000_000), releases.getValue("1.1.0").date)
        assertEquals(">=1.0.0", releases.getValue("1.1.0").requires)
        assertNull(releases.getValue("1.1.0").changelog)

        assertNull(releases.getValue("1.2.0").date)
        assertNull(releases.getValue("1.2.0").requires)

        assertNull(releases.getValue("0.9.0").requires)
        assertNull(releases.getValue("0.9.0").changelog)
        assertNull(releases.getValue("0.9.0").date)
    }

    @Test
    fun `getPlugins should list plugins of all known marketplaces and skip unknown ones`() {
        officialEntry()
        addMarketplaceEntry("third", name = "Third")
        activeRepositories = listOf(
            repository(OFFICIAL_MARKETPLACE_ID, plugins = mapOf("a" to pluginInfo("a", OFFICIAL_MARKETPLACE_ID, release("1.0.0")))),
            repository("ghost", plugins = mapOf("g" to pluginInfo("g", "ghost", release("1.0.0")))),
            repository(
                "third",
                plugins = mapOf(
                    "a" to pluginInfo("a", "third", release("2.0.0")),
                    "b" to pluginInfo("b", "third", release("1.0.0"))
                )
            )
        )

        val result = service().getPlugins()

        assertEquals(
            listOf(OFFICIAL_MARKETPLACE_ID to "a", "third" to "a", "third" to "b"),
            result.map { it.marketplaceId to it.id }
        )
        assertEquals("Third", result[1].marketplaceName)
        assertFalse(result[1].officialMarketplace)
    }

    @Test
    fun `getPlugins should return empty list without marketplaces`() {
        assertTrue(service().getPlugins().isEmpty())
    }

    // ========================================================================================
    // install / update / uninstall
    // ========================================================================================

    @Test
    fun `install should install plugin, emit update and return DTO`() {
        val dto = mockk<PluginDto>()
        every { updateManager.installPlugin("third", "p", "1.0.0") } returns PluginState.STARTED
        every { pluginService.getDto("p") } returns dto

        val result = service().install("third", "p", "1.0.0")

        assertSame(dto, result)
        verifyOrder {
            updateManager.installPlugin("third", "p", "1.0.0")
            pluginService.emitPlugin("p")
            pluginService.getDto("p")
        }
    }

    @Test
    fun `install should emit update even if installation fails`() {
        every { updateManager.installPlugin(any(), any(), any()) } throws IllegalStateException("incompatible")

        val e = assertFailsWith<IllegalStateException> { service().install("third", "p", null) }

        assertEquals("incompatible", e.message)
        verify(exactly = 1) { pluginService.emitPlugin("p") }
        verify(exactly = 0) { pluginService.getDto(any()) }
    }

    @Test
    fun `update should fail if plugin is not installed`() {
        assertFailsWith<IllegalArgumentException> { service().update("p", null) }

        verify(exactly = 0) { updateManager.installPlugin(any(), any(), any()) }
        verify(exactly = 0) { pluginService.emitPlugin(any()) }
    }

    @Test
    fun `update should fail if plugin is not in any marketplace`() {
        install("p", "1.0.0")
        every { updateManager.findPluginInfo("p") } returns null

        val e = assertFailsWith<IllegalArgumentException> { service().update("p", null) }

        assertTrue(e.message!!.contains("not found in any marketplace"))
        verify(exactly = 0) { updateManager.installPlugin(any(), any(), any()) }
    }

    @Test
    fun `update should install from the marketplace returned by the update manager`() {
        install("p", "1.0.0")
        val dto = mockk<PluginDto>()
        every { updateManager.findPluginInfo("p") } returns pluginInfo("p", "third", release("1.1.0"))
        every { updateManager.installPlugin("third", "p", "1.1.0") } returns PluginState.STARTED
        every { pluginService.getDto("p") } returns dto

        assertSame(dto, service().update("p", "1.1.0"))

        verify(exactly = 1) { updateManager.installPlugin("third", "p", "1.1.0") }
        verify(exactly = 1) { pluginService.emitPlugin("p") }
    }

    @Test
    fun `uninstall should uninstall plugin, emit update and return DTO`() {
        val dto = mockk<PluginDto>()
        every { updateManager.uninstall("p") } returns PluginState.STARTED
        every { pluginService.getDto("p") } returns dto

        assertSame(dto, service().uninstall("p"))

        verifyOrder {
            updateManager.uninstall("p")
            pluginService.emitPlugin("p")
            pluginService.getDto("p")
        }
    }

    @Test
    fun `uninstall should return null if no local version exists`() {
        every { updateManager.uninstall("p") } returns null
        every { pluginService.getDto("p") } returns null

        assertNull(service().uninstall("p"))
    }

    @Test
    fun `uninstall should emit update even if uninstall fails`() {
        every { updateManager.uninstall("p") } throws IllegalStateException("not from marketplace")

        assertFailsWith<IllegalStateException> { service().uninstall("p") }

        verify(exactly = 1) { pluginService.emitPlugin("p") }
        verify(exactly = 0) { pluginService.getDto(any()) }
    }

    // ========================================================================================
    // getLogo
    // ========================================================================================

    private val logoUrl = "https://example.com/logo.png"

    private fun setupLogoPlugin() {
        officialEntry()
        activeRepositories = listOf(
            repository(
                OFFICIAL_MARKETPLACE_ID,
                plugins = mapOf(
                    "p" to pluginInfo("p", OFFICIAL_MARKETPLACE_ID, release("1.0.0"), logoUrl = logoUrl),
                    "no-logo" to pluginInfo("no-logo", OFFICIAL_MARKETPLACE_ID, release("1.0.0"))
                )
            )
        )
    }

    @Test
    fun `getLogo should fetch logo and cache it`() {
        setupLogoPlugin()
        val bytes = byteArrayOf(1, 2, 3)
        every { httpClient.getBytes(URI(logoUrl), MarketplaceHttpClient.MAX_LOGO_SIZE) } returns bytes
        val service = service()

        assertContentEquals(bytes, service.getLogo(OFFICIAL_MARKETPLACE_ID, "p"))
        assertContentEquals(bytes, service.getLogo(OFFICIAL_MARKETPLACE_ID, "p"))

        verify(exactly = 1) { httpClient.getBytes(any(), any()) }
    }

    @Test
    fun `getLogo should return null and cache failures until refresh`() {
        setupLogoPlugin()
        every { httpClient.getBytes(any(), any()) } throws IOException("404")
        val service = service()

        assertNull(service.getLogo(OFFICIAL_MARKETPLACE_ID, "p"))
        assertNull(service.getLogo(OFFICIAL_MARKETPLACE_ID, "p"))
        verify(exactly = 1) { httpClient.getBytes(any(), any()) }

        val bytes = byteArrayOf(4, 5)
        every { httpClient.getBytes(any(), any()) } returns bytes
        service.refresh()

        assertContentEquals(bytes, service.getLogo(OFFICIAL_MARKETPLACE_ID, "p"))
        verify(exactly = 2) { httpClient.getBytes(any(), any()) }
    }

    @Test
    fun `getLogo should return null for unknown marketplace, unknown plugin or plugin without logo`() {
        setupLogoPlugin()
        val service = service()

        assertNull(service.getLogo("unknown", "p"))
        assertNull(service.getLogo(OFFICIAL_MARKETPLACE_ID, "unknown"))
        assertNull(service.getLogo(OFFICIAL_MARKETPLACE_ID, "no-logo"))
        verify(exactly = 0) { httpClient.getBytes(any(), any()) }
    }

    @Test
    fun `isSupportedLogoType should accept common image types`() {
        val service = service()

        listOf("image/png", "image/jpeg", "image/gif", "image/webp", "image/svg+xml", "IMAGE/PNG", "Image/Svg+Xml")
            .forEach { assertTrue(service.isSupportedLogoType(it), "$it should be supported") }
        listOf("image/bmp", "text/html", "application/octet-stream", "image/png; charset=utf-8", "", "png")
            .forEach { assertFalse(service.isSupportedLogoType(it), "$it should not be supported") }
        assertFalse(service.isSupportedLogoType(null))
    }
}
