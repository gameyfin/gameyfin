package org.gameyfin.app.core.notifications

import org.gameyfin.app.config.SsoConfigurationChanged
import org.gameyfin.app.core.plugins.marketplace.dto.MarketplacePluginDto
import org.gameyfin.app.core.plugins.marketplace.dto.MarketplacePluginStatus
import org.gameyfin.pluginapi.notifications.NotificationAction
import org.gameyfin.pluginapi.notifications.NotificationActionType
import org.gameyfin.pluginapi.notifications.NotificationRequest
import org.gameyfin.pluginapi.notifications.NotificationSeverity
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class SystemNotifications(
    private val store: NotificationStore,
    @param:Value($$"${spring.application.version:unknown}") private val installedVersion: String
) {
    companion object {
        private const val SSO_SOURCE = "core:sso"
        private const val RELEASE_SOURCE = "core:release"
        private const val PLUGIN_UPDATE_SOURCE = "core:plugin-update"
    }

    @EventListener
    fun ssoChanged(event: SsoConfigurationChanged) {
        store.producerState("sso") // Serialize concurrent saves and startup cleanup.
        store.resolve(SSO_SOURCE)
        store.publish(
            SSO_SOURCE, NotificationRequest(
                "Restart required",
                "The SSO configuration changed. **Restart Gameyfin** to apply it.",
                NotificationSeverity.INFO,
                actions = listOf(NotificationAction("restart", "Restart Gameyfin", NotificationActionType.RESTART))
            )
        )
        store.setProducerState("sso")
    }

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        val sso = store.producerState("sso")
        if (sso["BOOT_ID"] != store.bootId) store.resolve(SSO_SOURCE)
        val release = store.producerState("release")["LAST_VERSION"] as String?
        if (release != null && releaseTag(release) == releaseTag(installedVersion)) {
            store.resolve(RELEASE_SOURCE)
        }
        store.recoverInterrupted()
    }

    fun announceRelease(release: GithubRelease, installed: GithubRelease): Boolean {
        val previous = store.producerState("release")["LAST_VERSION"] as String?
        if (release.publishedAt <= installed.publishedAt) {
            store.resolve(RELEASE_SOURCE)
            return false
        }
        if (previous != null && releaseTag(previous) == releaseTag(release.version)) return false
        val version = release.version
        val url = release.url
        store.resolve(RELEASE_SOURCE)
        store.publish(
            RELEASE_SOURCE, NotificationRequest(
                "Gameyfin $version is available",
                "A new version of Gameyfin is available. Installed version: **$installedVersion**.",
                actions = listOf(
                    NotificationAction(
                        "release-notes",
                        "View release notes",
                        NotificationActionType.LINK,
                        url
                    )
                )
            )
        )
        store.setProducerState("release", version)
        return true
    }

    fun announcePluginUpdates(plugins: List<MarketplacePluginDto>): Int {
        if (plugins.isEmpty()) return 0
        val previousNotices = store.pluginUpdateNotices()
        var updateCount = 0

        for ((pluginId, entries) in plugins.groupBy { it.id }) {
            val updates = entries.filter { it.status == MarketplacePluginStatus.UPDATE_AVAILABLE }
            // Match the Marketplace UI's preference for the source the plugin was installed from.
            val update = updates.firstOrNull { it.marketplaceId == it.installedFromMarketplaceId } ?: updates.firstOrNull()
            val version = update?.latestCompatibleVersion
            val previous = previousNotices[pluginId]
            if (version != null) updateCount++
            if (previous?.version == version) continue

            if (previous != null) {
                store.resolve(PLUGIN_UPDATE_SOURCE, previous.notificationId)
                store.removePluginUpdateNotice(pluginId)
            }
            if (update != null && version != null) {
                val notificationId = store.publish(
                    PLUGIN_UPDATE_SOURCE, NotificationRequest(
                        "${update.name.take(150)} update available (${version.take(70)})",
                        "A compatible plugin update is ready to install.",
                        actions = listOf(
                            NotificationAction(
                                "marketplace", "Open Marketplace", NotificationActionType.LINK, "/administration/plugins#marketplace"
                            )
                        )
                    )
                )
                store.insertPluginUpdateNotice(pluginId, version, notificationId)
            }
        }
        return updateCount
    }
}
