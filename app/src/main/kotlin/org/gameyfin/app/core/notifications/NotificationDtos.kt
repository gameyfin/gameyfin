package org.gameyfin.app.core.notifications

import org.gameyfin.pluginapi.notifications.NotificationActionType
import org.gameyfin.pluginapi.notifications.NotificationSeverity
import java.time.Instant

data class NotificationActionDto(
    val key: String,
    val label: String,
    val type: NotificationActionType,
    val url: String?,
    val available: Boolean
)

data class NotificationDto(
    val id: String,
    val title: String,
    val body: String,
    val severity: NotificationSeverity,
    val source: String,
    val createdAt: Instant,
    val readAt: Instant?,
    val executionState: String,
    val shared: Boolean,
    val actions: List<NotificationActionDto>
)

data class NotificationPageDto(val items: List<NotificationDto>, val total: Long, val unreadCount: Long)

/** Payload-free invalidation: the client refetches only its authorized inbox. */
data class NotificationChange(val changed: Boolean = true)

internal data class NotificationActor(val id: Long, val admin: Boolean) {
    val recipients: List<String> get() = listOf("user:$id") + if (admin) listOf("admins") else emptyList()
}
