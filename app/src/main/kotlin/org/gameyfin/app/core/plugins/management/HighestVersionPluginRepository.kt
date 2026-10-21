package org.gameyfin.app.core.plugins.management

import io.github.oshai.kotlinlogging.KotlinLogging
import org.pf4j.PluginDescriptor
import org.pf4j.PluginDescriptorFinder
import org.pf4j.PluginRepository
import java.nio.file.Path

/**
 * Wraps another [PluginRepository] and makes sure that only one path per plugin ID is returned.
 *
 * Plugins can exist in multiple plugin roots at the same time (e.g. a bundled plugin which has been updated via the
 * marketplace). PF4J does not support loading the same plugin ID twice, so this repository picks one candidate:
 * 1. Compatible versions (the plugin's `Plugin-Requires` is satisfied by the system version) win over incompatible ones
 * 2. Higher versions win over lower versions
 * 3. On a tie, the plugin from the [preferredRoot] wins
 *
 * Paths for which no plugin descriptor can be found are passed through unchanged so the plugin manager can handle
 * (and log) them as before.
 *
 * Note: All dependencies are passed as functions because this repository is created while the plugin manager
 * is still being initialized (i.e. before the descriptor finder exists).
 */
class HighestVersionPluginRepository(
    val delegate: PluginRepository,
    private val descriptorFinder: () -> PluginDescriptorFinder,
    private val isCompatible: (PluginDescriptor) -> Boolean,
    private val compareVersions: (String, String) -> Int,
    private val preferredRoot: () -> Path?
) : PluginRepository {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    private data class Candidate(val index: Int, val path: Path, val descriptor: PluginDescriptor)

    override fun getPluginPaths(): List<Path> {
        val paths = delegate.pluginPaths
        val finder = descriptorFinder()

        val candidates = mutableListOf<Candidate>()
        val passThrough = mutableListOf<Pair<Int, Path>>()

        paths.forEachIndexed { index, path ->
            val descriptor = try {
                if (finder.isApplicable(path)) finder.find(path) else null
            } catch (e: Exception) {
                log.debug { "Could not read plugin descriptor of '$path': ${e.message}" }
                null
            }

            if (descriptor?.pluginId == null) {
                passThrough.add(index to path)
            } else {
                candidates.add(Candidate(index, path, descriptor))
            }
        }

        val winners = candidates
            .groupBy { it.descriptor.pluginId }
            .map { (pluginId, group) ->
                if (group.size == 1) return@map group.first()

                val winner = group.sortedWith(candidateComparator()).first()
                group.filter { it != winner }.forEach {
                    log.info {
                        "Plugin '$pluginId' found multiple times, using version ${winner.descriptor.version} " +
                                "from '${winner.path}' and ignoring version ${it.descriptor.version} from '${it.path}'"
                    }
                }
                winner
            }

        return (passThrough + winners.map { it.index to it.path })
            .sortedBy { it.first }
            .map { it.second }
    }

    override fun deletePluginPath(pluginPath: Path): Boolean {
        return delegate.deletePluginPath(pluginPath)
    }

    /**
     * Comparator which sorts the "best" candidate first.
     */
    private fun candidateComparator(): Comparator<Candidate> {
        val preferred = preferredRoot()?.toAbsolutePath()?.normalize()

        return Comparator<Candidate> { a, b ->
            // Compatible first
            val compatibleA = safeIsCompatible(a.descriptor)
            val compatibleB = safeIsCompatible(b.descriptor)
            if (compatibleA != compatibleB) return@Comparator if (compatibleA) -1 else 1

            // Higher version first
            val versionComparison = safeCompareVersions(b.descriptor.version, a.descriptor.version)
            if (versionComparison != 0) return@Comparator versionComparison

            // Preferred root first
            val preferredA = preferred != null && a.path.toAbsolutePath().normalize().startsWith(preferred)
            val preferredB = preferred != null && b.path.toAbsolutePath().normalize().startsWith(preferred)
            if (preferredA != preferredB) return@Comparator if (preferredA) -1 else 1

            0
        }
    }

    private fun safeIsCompatible(descriptor: PluginDescriptor): Boolean {
        return try {
            isCompatible(descriptor)
        } catch (_: Exception) {
            false
        }
    }

    private fun safeCompareVersions(v1: String?, v2: String?): Int {
        if (v1 == null || v2 == null) return compareValues(v1, v2)
        return try {
            compareVersions(v1, v2)
        } catch (_: Exception) {
            v1.compareTo(v2)
        }
    }
}
