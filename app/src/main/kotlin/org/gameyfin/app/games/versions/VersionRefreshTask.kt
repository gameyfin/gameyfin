package org.gameyfin.app.games.versions

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.gameyfin.app.core.tasks.TaskAction
import org.gameyfin.app.core.tasks.TaskDefinition
import org.gameyfin.app.core.tasks.TaskRegistry
import org.gameyfin.pluginapi.tasks.TaskContext
import org.gameyfin.pluginapi.tasks.TaskResult
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class VersionRefreshTask(
    private val registry: TaskRegistry,
    private val linkRepository: GameStoreLinkRepository,
    private val cacheService: StoreVersionCacheService,
    private val storeRegistry: GameStoreRegistry
) {
    companion object {
        private val log = KotlinLogging.logger {}

        const val KEY = "game-version-refresh"
        private const val DEFAULT_SCHEDULE = "0 0 4 * * *"
    }

    @PostConstruct
    fun register() {
        registry.register(
            TaskDefinition(
                key = KEY,
                name = "Refresh game versions",
                description = "Fetches and caches the available versions of linked games from the game store plugins",
                defaultSchedule = DEFAULT_SCHEDULE,
                action = TaskAction.Run { execute(it) }
            )
        )
    }

    fun execute(context: TaskContext): TaskResult {
        val links = linkRepository.findAll().sortedBy { it.id }
        if (links.isEmpty()) return TaskResult("No store links to refresh")

        val stores = storeRegistry.activeStores().associateBy { it.id }
        if (stores.isEmpty()) return TaskResult("Skipped: no game store plugin is enabled")

        var updated = 0
        var failed = 0
        var skipped = 0

        links.forEachIndexed { index, link ->
            context.throwIfCancelled()
            context.progress("Refreshing ${link.store} versions", index, links.size)

            val store = stores[link.store]
            if (store == null) {
                skipped++
                return@forEachIndexed
            }

            try {
                fetchAndCache(link, store)
                updated++
            } catch (e: Exception) {
                failed++
                log.warn(e) {
                    "Could not refresh versions for ${link.store} product ${link.storeAppId} (link ${link.id})"
                }
            }
        }

        context.throwIfCancelled()
        context.progress("Version refresh complete", links.size, links.size)
        if (failed > 0) {
            throw IllegalStateException("Version refresh updated $updated of ${links.size} links; $failed failed")
        }
        val skippedNote = if (skipped > 0) " ($skipped skipped because their store plugin is not enabled)" else ""
        return TaskResult("Refreshed versions for $updated store link(s)$skippedNote")
    }

    /** Fetches and caches the versions of a single store link right now (e.g. when files were replaced). */
    fun refreshLink(linkId: Long) {
        val link = linkRepository.findById(linkId).orElseThrow { NoSuchElementException("Link $linkId not found") }
        fetchAndCache(link, storeRegistry.require(link.store))
    }

    private fun fetchAndCache(link: GameStoreLink, store: ActiveGameStore) {
        val versions = store.provider.fetchVersions(link.storeAppId)
        cacheService.update(link.id!!, versions, Instant.now())
    }
}
