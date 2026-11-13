package org.gameyfin.app.core.notifications

import org.gameyfin.app.core.Role
import org.gameyfin.app.core.security.getCurrentAuth
import org.gameyfin.app.system.SystemService
import org.gameyfin.app.users.persistence.UserRepository
import org.gameyfin.pluginapi.notifications.*
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AuthenticationTrustResolverImpl
import org.springframework.stereotype.Service

@Service
class NotificationService(
    private val store: NotificationStore,
    private val users: UserRepository,
    private val callbacks: PluginNotificationActions,
    private val system: SystemService
) {
    internal fun actor(): NotificationActor {
        val auth = getCurrentAuth() ?: throw AccessDeniedException("Login required")
        if (!auth.isAuthenticated || AuthenticationTrustResolverImpl()
                .isAnonymous(auth)
        ) {
            throw AccessDeniedException("Login required")
        }
        val user = users.findByUsername(auth.name)?.takeIf { it.enabled }
            ?: throw AccessDeniedException("Login required")
        return NotificationActor(user.id!!, user.roles.any { it == Role.ADMIN || it == Role.SUPERADMIN })
    }

    fun page(archived: Boolean, page: Int, size: Int, severity: NotificationSeverity? = null): NotificationPageDto {
        val actor = actor()
        return NotificationPageDto(
            store.page(actor, archived, page, size, severity).map { dto(it, actor) },
            store.count(actor, archived, severity), store.count(actor, false)
        )
    }

    fun markRead(ids: Set<String>?) = store.mutate(actor(), ids, false)
    fun deleteArchived(ids: Set<String>?) = store.mutate(actor(), ids, true)

    fun execute(id: String, key: String) {
        val actor = actor()
        val entry = store.entry(actor, id)
        val action = entry.actions.find { it.key == key } ?: throw IllegalArgumentException("Unknown action")
        if (!available(entry, action, actor)) throw AccessDeniedException("Action unavailable")
        if (action.type == NotificationActionType.LINK) {
            store.mutate(actor, setOf(id), false)
            return
        }
        val claimed = store.claim(actor, id, key)
        try {
            // Recheck current account permissions after acquiring the execution claim.
            val current = actor()
            check(claimed.recipient in current.recipients) { "Notification unavailable" }
            check(available(claimed, action, current)) { "Action unavailable" }
            when (action.type) {
                NotificationActionType.RESTART -> system.restart()
                NotificationActionType.PLUGIN -> callbacks.execute(
                    entry.source, action.key, current.admin,
                    NotificationActionContext(current.id, entry.notificationId, entry.executionId, action.parameters)
                )

                else -> error("Unsupported action")
            }
            store.finish(claimed, true)
        } catch (e: Throwable) {
            try {
                store.finish(claimed, false)
            } catch (finishFailure: Throwable) {
                e.addSuppressed(finishFailure)
            }
            throw e
        }
    }

    /** The caller receives a facade bound to its identity, never a source parameter it can impersonate. */
    fun forPlugin(pluginId: String): GameyfinNotificationService = object : GameyfinNotificationService {
        override fun publish(notification: NotificationRequest): String =
            store.publish("plugin:$pluginId", notification)

        override fun resolve(notificationId: String) = store.resolve("plugin:$pluginId", notificationId)
    }

    private fun available(entry: InboxEntry, action: NotificationAction, actor: NotificationActor): Boolean {
        if (action.type == NotificationActionType.LINK) return action.url?.let(NotificationStore::safeLink) == true
        if (entry.executionState in setOf("RUNNING", "SUCCEEDED")) return false
        if (entry.executionAction != null && entry.executionAction != action.key) return false
        return when (action.type) {
            NotificationActionType.RESTART -> actor.admin
            NotificationActionType.PLUGIN -> callbacks.available(entry.source, action.key, actor.admin)
            else -> false
        }
    }

    private fun dto(entry: InboxEntry, actor: NotificationActor) = NotificationDto(
        entry.id, entry.title, entry.body,
        entry.severity, entry.source, entry.createdAt, entry.readAt, entry.executionState, entry.recipient == "admins",
        entry.actions.map { NotificationActionDto(it.key, it.label, it.type, it.url, available(entry, it, actor)) })
}
