package org.gameyfin.pluginapi.notifications

import org.pf4j.ExtensionPoint

enum class NotificationSeverity { INFO, SUCCESS, WARNING, ERROR }
enum class NotificationAudience { ADMINS, ALL_USERS, USERS }
enum class NotificationActionType { LINK, RESTART, PLUGIN }
enum class NotificationActionPermission { ADMIN, RECIPIENT }

/** User IDs are required only for USERS. ALL_USERS snapshots existing accounts at publication time. */
data class NotificationRecipients(
    val audience: NotificationAudience = NotificationAudience.ADMINS,
    val userIds: Set<Long> = emptySet()
)

/**
 * Keys are unique within a notification. PLUGIN keys identify a callback belonging to the publishing plugin.
 * Parameters are stored on the server; a browser cannot override them when executing an action.
 */
data class NotificationAction(
    val key: String,
    val label: String,
    val type: NotificationActionType,
    val url: String? = null,
    val parameters: Map<String, String> = emptyMap()
)

data class NotificationRequest(
    val title: String,
    val body: String,
    val severity: NotificationSeverity = NotificationSeverity.INFO,
    val recipients: NotificationRecipients = NotificationRecipients(),
    val actions: List<NotificationAction> = emptyList()
)

/** Provided by PluginContext, scoped to the calling plugin. Notifications survive plugin restarts. */
interface GameyfinNotificationService {
    fun publish(notification: NotificationRequest): String

    /** Removes a notification published by this plugin, including its archived entries. */
    fun resolve(notificationId: String)
}

/**
 * Registered while the plugin is started. A successful action consumes server actions for that inbox.
 * Failed/interrupted actions may be retried explicitly. Use executionId as an idempotency key for side effects:
 * the host cannot atomically commit an external effect together with its execution receipt.
 * Implementations must validate parameters and bound blocking I/O with timeouts.
 */
interface NotificationActionHandler : ExtensionPoint {
    val key: String
    val permission: NotificationActionPermission get() = NotificationActionPermission.ADMIN
    fun execute(context: NotificationActionContext)
}

data class NotificationActionContext(
    val actorId: Long,
    val notificationId: String,
    val executionId: String,
    val parameters: Map<String, String>
)
