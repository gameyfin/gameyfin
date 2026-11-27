package org.gameyfin.app.core.notifications

import jakarta.persistence.EntityManager
import org.gameyfin.app.users.UserStateChanged
import org.gameyfin.app.users.persistence.UserRepository
import org.gameyfin.pluginapi.notifications.*
import org.springframework.data.domain.PageRequest
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionalEventListener
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.time.Instant
import java.util.*

internal data class InboxEntry(
    val id: String, val notificationId: String, val recipient: String, val source: String,
    val title: String, val body: String, val severity: NotificationSeverity,
    val actions: List<NotificationAction>, val createdAt: Instant, val readAt: Instant?,
    val executionId: String, val executionState: String, val executionAction: String?
)

/** All inbox mutations use database locks; events become visible only after the transaction commits. */
@Repository
@Transactional
class NotificationStore(
    private val notifications: NotificationRepository,
    private val inbox: NotificationInboxRepository,
    private val producers: NotificationProducerRepository,
    private val notices: PluginUpdateNoticeRepository,
    private val users: UserRepository,
    private val entities: EntityManager,
    private val mapper: ObjectMapper,
    private val events: NotificationEvents
) {
    val bootId: String = UUID.randomUUID().toString()

    internal fun page(
        actor: NotificationActor,
        archived: Boolean,
        page: Int,
        size: Int,
        severity: NotificationSeverity? = null
    ): List<InboxEntry> {
        require(page in 0..100_000 && size in 1..100) { "Invalid page" }
        return inbox.page(actor.recipients, archived, severity, PageRequest.of(page, size)).map(::entry)
    }

    internal fun count(actor: NotificationActor, archived: Boolean, severity: NotificationSeverity? = null): Long =
        inbox.countVisible(actor.recipients, archived, severity)

    fun publish(source: String, request: NotificationRequest): String {
        validate(request)
        val recipients = when (request.recipients.audience) {
            NotificationAudience.ADMINS -> listOf("admins" to null)
            NotificationAudience.ALL_USERS -> users.allIds().map { "user:$it" to it }

            NotificationAudience.USERS -> {
                val ids = request.recipients.userIds
                require(users.countByIdIn(ids) == ids.size.toLong()) { "Unknown notification recipient" }
                ids.map { "user:$it" to it }
            }
        }
        val id = UUID.randomUUID().toString()
        if (recipients.isEmpty()) return id
        val notification = AppNotification(
            id, source, request.title, request.body, request.severity,
            mapper.writeValueAsString(request.actions), Instant.now()
        )
        entities.persist(notification)
        recipients.forEach { (recipient, userId) ->
            val row = NotificationInbox(
                UUID.randomUUID().toString(), notification, recipient, userId, UUID.randomUUID().toString()
            )
            entities.persist(row)
        }
        events.changed(recipients.map { it.first }.toSet())
        return id
    }

    fun resolve(source: String, notificationId: String? = null) {
        val recipients = inbox.recipients(source, notificationId)
        notifications.resolve(source, notificationId)
        events.changed(recipients)
    }

    internal fun mutate(actor: NotificationActor, ids: Set<String>?, delete: Boolean) {
        require(ids == null || ids.size <= 100) { "Select at most 100 notifications" }
        if (ids?.isEmpty() == true) return
        val entries = if (ids == null) inbox.lockAllEligible(actor.recipients, delete)
        else inbox.lockSelected(actor.recipients, ids)
        if (ids != null && entries.map { it.id }.toSet() != ids) throw AccessDeniedException("Notification unavailable")
        require(entries.none { it.executionState == "RUNNING" }) { "An action is still running" }
        if (delete) require(entries.all { it.readAt != null }) { "Only archived notifications can be deleted" }
        if (delete) inbox.deleteAll(entries)
        else entries.filter { it.readAt == null }.forEach { it.readAt = Instant.now() }
        notifications.removeOrphans()
        events.changed(entries.map { it.recipient }.toSet())
    }

    internal fun entry(actor: NotificationActor, id: String): InboxEntry {
        val record = inbox.findByIdAndRecipientIn(id, actor.recipients)
            ?: throw AccessDeniedException("Notification unavailable")
        return entry(record)
    }

    /** Persist the claim before any plugin or restart side effect. Retries retain the same execution ID. */
    internal fun claim(actor: NotificationActor, id: String, actionKey: String): InboxEntry {
        val record = inbox.lockSelected(actor.recipients, setOf(id)).singleOrNull()
            ?: throw AccessDeniedException("Notification unavailable")
        val entry = entry(record)
        require(entry.executionState !in setOf("RUNNING", "SUCCEEDED")) { "Action already running or completed" }
        require(entry.executionAction == null || entry.executionAction == actionKey) { "Retry the original action" }
        record.executionState = "RUNNING"
        record.executionAction = actionKey
        record.executionBoot = bootId
        events.changed(setOf(entry.recipient))
        return entry
    }

    internal fun finish(entry: InboxEntry, success: Boolean) {
        inbox.finish(entry.id, bootId, if (success) "SUCCEEDED" else "FAILED", if (success) Instant.now() else null)
        events.changed(setOf(entry.recipient))
    }

    fun recoverInterrupted() {
        inbox.recoverInterrupted(bootId)
    }

    fun cleanup(retentionDays: Int): Int {
        require(retentionDays > 0) { "Retention must be at least one day" }
        val cutoff = Instant.now().minusSeconds(retentionDays.toLong() * 86400)
        val recipients = inbox.expiredRecipients(cutoff)
        val count = inbox.deleteExpired(cutoff)
        notifications.removeOrphans()
        events.changed(recipients)
        return count
    }

    internal fun producerState(key: String) = producers.lock(key)

    internal fun pluginUpdateNotices(): Map<String, PluginUpdateNotice> {
        // Lock the producer even when no plugin has a notice yet.
        producerState("plugin-updates")
        return notices.findAll().associateBy { it.pluginId }
    }

    internal fun insertPluginUpdateNotice(pluginId: String, version: String, notificationId: String) {
        notices.save(PluginUpdateNotice(pluginId, version, notificationId))
    }

    internal fun removePluginUpdateNotice(pluginId: String) {
        notices.deleteById(pluginId)
    }

    fun invalidatePlugin(pluginId: String) {
        events.changed(inbox.recipients("plugin:$pluginId", null))
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun userChanged(event: UserStateChanged) {
        if (event.update.user == null) notifications.removeOrphans()
    }

    private fun entry(row: NotificationInbox): InboxEntry = with(row.notification) {
        InboxEntry(
            row.id, id, row.recipient, source, title, body, severity,
            mapper.readValue(actions, Array<NotificationAction>::class.java).toList(),
            createdAt, row.readAt, row.executionId, row.executionState, row.executionAction
        )
    }

    companion object {
        internal fun validate(request: NotificationRequest) {
            require(request.title.isNotBlank() && request.title.length <= 250) { "Title must contain 1–250 characters" }
            require(request.body.length <= 100_000) { "Notification body is too long" }
            require(request.actions.size <= 10 && request.actions.map { it.key }
                .distinct().size == request.actions.size) { "Invalid action list" }
            require((request.recipients.audience == NotificationAudience.USERS) == request.recipients.userIds.isNotEmpty()) { "Specify user IDs only for USERS" }
            request.actions.forEach {
                require(it.key.matches(Regex("[a-zA-Z0-9._-]{1,100}"))) { "Invalid action key" }
                require(it.label.isNotBlank() && it.label.length <= 100) { "Invalid action label" }
                require(it.parameters.size <= 50 && it.parameters.all { (k, v) -> k.length <= 100 && v.length <= 10_000 }) { "Action parameters are too large" }
                if (it.type == NotificationActionType.LINK) require(it.url?.let(::safeLink) == true) { "Invalid action URL" }
                else require(it.url == null) { "Only link actions accept URLs" }
            }
        }

        internal fun safeLink(url: String): Boolean = try {
            val uri = URI(url)
            !url.contains('\\') && url.none { it.isISOControl() } &&
                    ((url.startsWith('/') && !url.startsWith("//") && uri.scheme == null) ||
                            (uri.scheme?.lowercase() in setOf(
                                "http",
                                "https"
                            ) && !uri.host.isNullOrBlank() && uri.userInfo == null))
        } catch (_: Exception) {
            false
        }
    }
}
