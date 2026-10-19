package org.gameyfin.app.core.plugins.management

import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.pf4j.*
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HighestVersionPluginRepositoryTest {

    private lateinit var delegate: PluginRepository
    private lateinit var finder: PluginDescriptorFinder
    private val versionManager = DefaultVersionManager()

    private val installedRoot = Path("/gameyfin/plugins-installed")
    private val bundledRoot = Path("/gameyfin/plugins")

    @BeforeEach
    fun setup() {
        delegate = mockk()
        finder = mockk()
        every { finder.isApplicable(any()) } returns true
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    private fun repository(
        isCompatible: (PluginDescriptor) -> Boolean = { true },
        compareVersions: (String, String) -> Int = { v1, v2 -> versionManager.compareVersions(v1, v2) },
        preferredRoot: () -> Path? = { installedRoot }
    ) = HighestVersionPluginRepository(
        delegate = delegate,
        descriptorFinder = { finder },
        isCompatible = isCompatible,
        compareVersions = compareVersions,
        preferredRoot = preferredRoot
    )

    private fun descriptor(pluginId: String?, version: String?, requires: String = "*"): PluginDescriptor {
        return DefaultPluginDescriptor(pluginId, null, null, version, requires, null, null)
    }

    private fun givenPaths(vararg paths: Pair<Path, PluginDescriptor?>) {
        every { delegate.pluginPaths } returns paths.map { it.first }
        paths.forEach { (path, descriptor) ->
            if (descriptor == null) {
                every { finder.isApplicable(path) } returns false
            } else {
                every { finder.find(path) } returns descriptor
            }
        }
    }

    @Test
    fun `should return empty list when delegate has no paths`() {
        every { delegate.pluginPaths } returns emptyList()

        assertTrue(repository().pluginPaths.isEmpty())
    }

    @Test
    fun `should return single plugin path unchanged`() {
        val path = bundledRoot.resolve("plugin-a.jar")
        givenPaths(path to descriptor("plugin-a", "1.0.0"))

        assertEquals(listOf(path), repository().pluginPaths)
    }

    @Test
    fun `should keep plugins with different IDs`() {
        val a = bundledRoot.resolve("plugin-a.jar")
        val b = installedRoot.resolve("plugin-b.jar")
        givenPaths(a to descriptor("plugin-a", "1.0.0"), b to descriptor("plugin-b", "1.0.0"))

        assertEquals(listOf(a, b), repository().pluginPaths)
    }

    @Test
    fun `should pick the higher version when plugin ID exists multiple times`() {
        val old = bundledRoot.resolve("plugin-a-1.0.0.jar")
        val new = installedRoot.resolve("plugin-a-1.2.0.jar")
        givenPaths(old to descriptor("plugin-a", "1.0.0"), new to descriptor("plugin-a", "1.2.0"))

        assertEquals(listOf(new), repository().pluginPaths)
    }

    @Test
    fun `should pick the higher version even if it is not in the preferred root`() {
        val bundled = bundledRoot.resolve("plugin-a-2.0.0.jar")
        val installed = installedRoot.resolve("plugin-a-1.5.0.jar")
        givenPaths(installed to descriptor("plugin-a", "1.5.0"), bundled to descriptor("plugin-a", "2.0.0"))

        assertEquals(listOf(bundled), repository().pluginPaths)
    }

    @Test
    fun `should compare versions semantically instead of lexicographically`() {
        val v9 = bundledRoot.resolve("plugin-a-1.9.0.jar")
        val v10 = installedRoot.resolve("plugin-a-1.10.0.jar")
        givenPaths(v9 to descriptor("plugin-a", "1.9.0"), v10 to descriptor("plugin-a", "1.10.0"))

        assertEquals(listOf(v10), repository().pluginPaths)
    }

    @Test
    fun `should prefer compatible version over higher incompatible version`() {
        val compatible = bundledRoot.resolve("plugin-a-1.0.0.jar")
        val incompatible = installedRoot.resolve("plugin-a-2.0.0.jar")
        givenPaths(
            compatible to descriptor("plugin-a", "1.0.0", requires = ">=1.0.0"),
            incompatible to descriptor("plugin-a", "2.0.0", requires = ">=99.0.0")
        )

        val repository = repository(isCompatible = { it.requires != ">=99.0.0" })

        assertEquals(listOf(compatible), repository.pluginPaths)
    }

    @Test
    fun `should pick highest version when all candidates are incompatible`() {
        val v1 = bundledRoot.resolve("plugin-a-1.0.0.jar")
        val v2 = installedRoot.resolve("plugin-a-2.0.0.jar")
        givenPaths(v1 to descriptor("plugin-a", "1.0.0"), v2 to descriptor("plugin-a", "2.0.0"))

        assertEquals(listOf(v2), repository(isCompatible = { false }).pluginPaths)
    }

    @Test
    fun `should prefer plugin from preferred root on version tie`() {
        val bundled = bundledRoot.resolve("plugin-a.jar")
        val installed = installedRoot.resolve("plugin-a.jar")
        givenPaths(bundled to descriptor("plugin-a", "1.0.0"), installed to descriptor("plugin-a", "1.0.0"))

        assertEquals(listOf(installed), repository().pluginPaths)
    }

    @Test
    fun `should prefer plugin from preferred root on version tie regardless of order`() {
        val bundled = bundledRoot.resolve("plugin-a.jar")
        val installed = installedRoot.resolve("plugin-a.jar")
        givenPaths(installed to descriptor("plugin-a", "1.0.0"), bundled to descriptor("plugin-a", "1.0.0"))

        assertEquals(listOf(installed), repository().pluginPaths)
    }

    @Test
    fun `should normalize preferred root when comparing paths`() {
        val bundled = bundledRoot.resolve("plugin-a.jar")
        val installed = installedRoot.resolve("plugin-a.jar")
        givenPaths(bundled to descriptor("plugin-a", "1.0.0"), installed to descriptor("plugin-a", "1.0.0"))

        val repository = repository(preferredRoot = { installedRoot.resolve("sub").resolve("..") })

        assertEquals(listOf(installed), repository.pluginPaths)
    }

    @Test
    fun `should keep first candidate on full tie without preferred root`() {
        val first = bundledRoot.resolve("plugin-a.jar")
        val second = installedRoot.resolve("plugin-a.jar")
        givenPaths(first to descriptor("plugin-a", "1.0.0"), second to descriptor("plugin-a", "1.0.0"))

        assertEquals(listOf(first), repository(preferredRoot = { null }).pluginPaths)
    }

    @Test
    fun `should pass through paths for which finder is not applicable`() {
        val unknown = bundledRoot.resolve("data")
        val plugin = bundledRoot.resolve("plugin-a.jar")
        givenPaths(unknown to null, plugin to descriptor("plugin-a", "1.0.0"))

        assertEquals(listOf(unknown, plugin), repository().pluginPaths)
        verify(exactly = 0) { finder.find(unknown) }
    }

    @Test
    fun `should pass through paths for which descriptor cannot be read`() {
        val broken = bundledRoot.resolve("broken.jar")
        val plugin = bundledRoot.resolve("plugin-a.jar")
        every { delegate.pluginPaths } returns listOf(broken, plugin)
        every { finder.find(broken) } throws PluginRuntimeException("Cannot read manifest")
        every { finder.find(plugin) } returns descriptor("plugin-a", "1.0.0")

        assertEquals(listOf(broken, plugin), repository().pluginPaths)
    }

    @Test
    fun `should pass through paths when isApplicable throws`() {
        val broken = bundledRoot.resolve("broken")
        every { delegate.pluginPaths } returns listOf(broken)
        every { finder.isApplicable(broken) } throws IllegalStateException("boom")

        assertEquals(listOf(broken), repository().pluginPaths)
    }

    @Test
    fun `should pass through paths whose descriptor has no plugin ID`() {
        val noId1 = bundledRoot.resolve("no-id-1.jar")
        val noId2 = installedRoot.resolve("no-id-2.jar")
        givenPaths(noId1 to descriptor(null, "1.0.0"), noId2 to descriptor(null, "2.0.0"))

        // Paths without ID must not be grouped/deduplicated
        assertEquals(listOf(noId1, noId2), repository().pluginPaths)
    }

    @Test
    fun `should preserve original order of remaining paths`() {
        val aOld = bundledRoot.resolve("plugin-a-1.0.0.jar")
        val passThrough = bundledRoot.resolve("state")
        val b = bundledRoot.resolve("plugin-b.jar")
        val aNew = installedRoot.resolve("plugin-a-2.0.0.jar")
        val c = installedRoot.resolve("plugin-c.jar")
        givenPaths(
            aOld to descriptor("plugin-a", "1.0.0"),
            passThrough to null,
            b to descriptor("plugin-b", "1.0.0"),
            aNew to descriptor("plugin-a", "2.0.0"),
            c to descriptor("plugin-c", "0.1.0")
        )

        assertEquals(listOf(passThrough, b, aNew, c), repository().pluginPaths)
    }

    @Test
    fun `should deduplicate more than two candidates of the same plugin`() {
        val v1 = bundledRoot.resolve("plugin-a-1.jar")
        val v3 = bundledRoot.resolve("plugin-a-3.jar")
        val v2 = installedRoot.resolve("plugin-a-2.jar")
        givenPaths(
            v1 to descriptor("plugin-a", "1.0.0"),
            v3 to descriptor("plugin-a", "3.0.0"),
            v2 to descriptor("plugin-a", "2.0.0")
        )

        assertEquals(listOf(v3), repository().pluginPaths)
    }

    @Test
    fun `should treat candidate as incompatible when isCompatible throws`() {
        val throwing = installedRoot.resolve("plugin-a-2.0.0.jar")
        val compatible = bundledRoot.resolve("plugin-a-1.0.0.jar")
        givenPaths(
            throwing to descriptor("plugin-a", "2.0.0", requires = "boom"),
            compatible to descriptor("plugin-a", "1.0.0")
        )

        val repository = repository(isCompatible = {
            if (it.requires == "boom") throw IllegalArgumentException("Invalid constraint") else true
        })

        assertEquals(listOf(compatible), repository.pluginPaths)
    }

    @Test
    fun `should fall back to string comparison when compareVersions throws`() {
        val lower = installedRoot.resolve("plugin-a-a.jar")
        val higher = bundledRoot.resolve("plugin-a-b.jar")
        givenPaths(lower to descriptor("plugin-a", "alpha"), higher to descriptor("plugin-a", "beta"))

        val repository = repository(compareVersions = { _, _ -> throw IllegalArgumentException("Invalid version") })

        assertEquals(listOf(higher), repository.pluginPaths)
    }

    @Test
    fun `should prefer candidate with version over candidate without version`() {
        val noVersion = installedRoot.resolve("plugin-a-none.jar")
        val withVersion = bundledRoot.resolve("plugin-a-1.0.0.jar")
        givenPaths(noVersion to descriptor("plugin-a", null), withVersion to descriptor("plugin-a", "1.0.0"))

        assertEquals(listOf(withVersion), repository().pluginPaths)
    }

    @Test
    fun `should not invoke comparators for single candidates`() {
        val path = bundledRoot.resolve("plugin-a.jar")
        givenPaths(path to descriptor("plugin-a", "1.0.0"))
        var compatibilityChecks = 0
        var versionComparisons = 0
        var preferredRootCalls = 0

        val repository = repository(
            isCompatible = { compatibilityChecks++; true },
            compareVersions = { _, _ -> versionComparisons++; 0 },
            preferredRoot = { preferredRootCalls++; installedRoot }
        )

        assertEquals(listOf(path), repository.pluginPaths)
        assertEquals(0, compatibilityChecks)
        assertEquals(0, versionComparisons)
        assertEquals(0, preferredRootCalls)
    }

    @Test
    fun `should resolve descriptor finder lazily`() {
        var finderRequested = false
        val repository = HighestVersionPluginRepository(
            delegate = delegate,
            descriptorFinder = { finderRequested = true; finder },
            isCompatible = { true },
            compareVersions = { _, _ -> 0 },
            preferredRoot = { null }
        )

        assertFalse(finderRequested)

        every { delegate.pluginPaths } returns emptyList()
        repository.pluginPaths

        assertTrue(finderRequested)
    }

    @Test
    fun `should re-evaluate delegate paths on every call`() {
        val a = bundledRoot.resolve("plugin-a.jar")
        val b = bundledRoot.resolve("plugin-b.jar")
        every { finder.find(a) } returns descriptor("plugin-a", "1.0.0")
        every { finder.find(b) } returns descriptor("plugin-b", "1.0.0")
        every { delegate.pluginPaths } returnsMany listOf(listOf(a), listOf(a, b))

        val repository = repository()

        assertEquals(listOf(a), repository.pluginPaths)
        assertEquals(listOf(a, b), repository.pluginPaths)
    }

    @Test
    fun `deletePluginPath should delegate and return delegate result`() {
        val path = installedRoot.resolve("plugin-a.jar")
        every { delegate.deletePluginPath(path) } returns true

        assertTrue(repository().deletePluginPath(path))
        verify(exactly = 1) { delegate.deletePluginPath(path) }
    }

    @Test
    fun `deletePluginPath should return false when delegate returns false`() {
        val path = installedRoot.resolve("plugin-a.jar")
        every { delegate.deletePluginPath(path) } returns false

        assertFalse(repository().deletePluginPath(path))
    }

    @Test
    fun `deletePluginPath should propagate delegate exceptions`() {
        val path = installedRoot.resolve("plugin-a.jar")
        every { delegate.deletePluginPath(path) } throws PluginRuntimeException("Cannot delete")

        assertFailsWith<PluginRuntimeException> { repository().deletePluginPath(path) }
    }

    @Test
    fun `delegate should be exposed`() {
        assertEquals(delegate, repository().delegate)
    }
}
