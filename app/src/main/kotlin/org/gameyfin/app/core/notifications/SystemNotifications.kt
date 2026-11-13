package org.gameyfin.app.core.notifications

import org.gameyfin.app.config.SsoConfigurationChanged
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
}
