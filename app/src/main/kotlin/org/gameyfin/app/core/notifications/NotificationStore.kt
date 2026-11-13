package org.gameyfin.app.core.notifications

import org.gameyfin.app.users.UserStateChanged
import org.gameyfin.pluginapi.notifications.*
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionalEventListener
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
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
    private val jdbc: NamedParameterJdbcTemplate, private val mapper: ObjectMapper,
    private val events: NotificationEvents
) {
    val bootId: String = UUID.randomUUID().toString()

    private val select = "SELECT i.*, n.SOURCE, n.TITLE, n.BODY, n.SEVERITY, n.ACTIONS, n.CREATED_AT " +
            "FROM NOTIFICATION_INBOX i JOIN APP_NOTIFICATION n ON n.ID = i.NOTIFICATION_ID"

    internal fun page(
        actor: NotificationActor,
        archived: Boolean,
        page: Int,
        size: Int,
        severity: NotificationSeverity? = null
    ): List<InboxEntry> {
        require(page in 0..100_000 && size in 1..100) { "Invalid page" }
        return jdbc.query(
            "$select WHERE i.RECIPIENT IN (:recipients) AND i.READ_AT IS ${if (archived) "NOT " else ""}NULL " +
                    (if (severity != null) "AND n.SEVERITY = :severity " else "") + "ORDER BY n.CREATED_AT DESC, i.ID DESC LIMIT :size OFFSET :offset",
            mapOf(
                "recipients" to actor.recipients,
                "size" to size,
                "offset" to page.toLong() * size,
                "severity" to severity?.name
            )
        ) { rs, _ -> row(rs) }
    }

    internal fun count(actor: NotificationActor, archived: Boolean, severity: NotificationSeverity? = null): Long =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM NOTIFICATION_INBOX i JOIN APP_NOTIFICATION n ON n.ID = i.NOTIFICATION_ID " +
                    "WHERE i.RECIPIENT IN (:recipients) AND i.READ_AT IS ${if (archived) "NOT " else ""}NULL " +
                    (if (severity != null) "AND n.SEVERITY = :severity" else ""),
            mapOf("recipients" to actor.recipients, "severity" to severity?.name), Long::class.java
        )!!

    fun publish(source: String, request: NotificationRequest): String {
        validate(request)
        val recipients = when (request.recipients.audience) {
            NotificationAudience.ADMINS -> listOf("admins" to null)
            NotificationAudience.ALL_USERS -> jdbc.queryForList(
                "SELECT ID FROM USERS",
                emptyMap<String, Any>(),
                Long::class.java
            ).filterNotNull()
                .map { "user:$it" to it }

            NotificationAudience.USERS -> {
                val ids = request.recipients.userIds
                val found =
                    jdbc.queryForList("SELECT ID FROM USERS WHERE ID IN (:ids)", mapOf("ids" to ids), Long::class.java)
                        .toSet()
                require(found == ids) { "Unknown notification recipient" }
                ids.map { "user:$it" to it }
            }
        }
        val id = UUID.randomUUID().toString()
        if (recipients.isEmpty()) return id
        jdbc.update(
            "INSERT INTO APP_NOTIFICATION (ID, SOURCE, TITLE, BODY, SEVERITY, ACTIONS, CREATED_AT) " +
                    "VALUES (:id, :source, :title, :body, :severity, :actions, :created)", mapOf(
                "id" to id,
                "source" to source,
                "title" to request.title,
                "body" to request.body,
                "severity" to request.severity.name,
                "actions" to mapper.writeValueAsString(request.actions),
                "created" to now()
            )
        )
        recipients.forEach { (recipient, userId) ->
            jdbc.update(
                "INSERT INTO NOTIFICATION_INBOX (ID, NOTIFICATION_ID, RECIPIENT, USER_ID, EXECUTION_ID) " +
                        "VALUES (:id, :notification, :recipient, :user, :execution)", mapOf(
                    "id" to UUID.randomUUID().toString(),
                    "notification" to id,
                    "recipient" to recipient,
                    "user" to userId,
                    "execution" to UUID.randomUUID().toString()
                )
            )
        }
        events.changed(recipients.map { it.first }.toSet())
        return id
    }

    fun resolve(source: String, notificationId: String? = null) {
        val parameters = mapOf("source" to source, "id" to notificationId)
        val filter = "n.SOURCE = :source" + if (notificationId == null) "" else " AND n.ID = :id"
        val recipients = jdbc.queryForList(
            "SELECT i.RECIPIENT FROM NOTIFICATION_INBOX i JOIN APP_NOTIFICATION n " +
                    "ON n.ID = i.NOTIFICATION_ID WHERE $filter", parameters, String::class.java
        ).filterNotNull().toSet()
        jdbc.update("DELETE FROM APP_NOTIFICATION n WHERE $filter", parameters)
        events.changed(recipients)
    }

    internal fun mutate(actor: NotificationActor, ids: Set<String>?, delete: Boolean) {
        require(ids == null || ids.size <= 100) { "Select at most 100 notifications" }
        if (ids?.isEmpty() == true) return
        val entries: List<InboxEntry> = jdbc.query(
            "$select WHERE i.RECIPIENT IN (:recipients)" +
                    (if (ids == null) " AND i.READ_AT IS ${if (delete) "NOT " else ""}NULL AND i.EXECUTION_STATE <> 'RUNNING'"
                    else " AND i.ID IN (:ids)") + " ORDER BY i.ID FOR UPDATE",
            mapOf("recipients" to actor.recipients, "ids" to ids)
        ) { rs, _ -> row(rs) }
        if (ids != null && entries.map { it.id }.toSet() != ids) throw AccessDeniedException("Notification unavailable")
        require(entries.none { it.executionState == "RUNNING" }) { "An action is still running" }
        if (delete) require(entries.all { it.readAt != null }) { "Only archived notifications can be deleted" }
        entries.forEach {
            if (delete) jdbc.update("DELETE FROM NOTIFICATION_INBOX WHERE ID = :id", mapOf("id" to it.id))
            else jdbc.update(
                "UPDATE NOTIFICATION_INBOX SET READ_AT = COALESCE(READ_AT, :now) WHERE ID = :id",
                mapOf("id" to it.id, "now" to now())
            )
        }
        removeOrphans()
        events.changed(entries.map { it.recipient }.toSet())
    }

    internal fun entry(actor: NotificationActor, id: String, lock: Boolean = false): InboxEntry = jdbc.query(
        "$select WHERE i.ID = :id AND i.RECIPIENT IN (:recipients)${if (lock) " FOR UPDATE" else ""}",
        mapOf("id" to id, "recipients" to actor.recipients)
    ) { rs, _ -> row(rs) }.singleOrNull()
        ?: throw AccessDeniedException("Notification unavailable")

    /** Persist the claim before any plugin or restart side effect. Retries retain the same execution ID. */
    internal fun claim(actor: NotificationActor, id: String, actionKey: String): InboxEntry {
        val entry = entry(actor, id, true)
        require(entry.executionState !in setOf("RUNNING", "SUCCEEDED")) { "Action already running or completed" }
        require(entry.executionAction == null || entry.executionAction == actionKey) { "Retry the original action" }
        jdbc.update(
            "UPDATE NOTIFICATION_INBOX SET EXECUTION_STATE = 'RUNNING', EXECUTION_ACTION = :action, " +
                    "EXECUTION_BOOT = :boot WHERE ID = :id", mapOf("id" to id, "action" to actionKey, "boot" to bootId)
        )
        events.changed(setOf(entry.recipient))
        return entry
    }

    internal fun finish(entry: InboxEntry, success: Boolean) {
        jdbc.update(
            "UPDATE NOTIFICATION_INBOX SET EXECUTION_STATE = :state" +
                    (if (success) ", READ_AT = COALESCE(READ_AT, :now)" else "") +
                    " WHERE ID = :id AND EXECUTION_STATE = 'RUNNING' AND EXECUTION_BOOT = :boot",
            mapOf("id" to entry.id, "state" to if (success) "SUCCEEDED" else "FAILED", "now" to now(), "boot" to bootId)
        )
        events.changed(setOf(entry.recipient))
    }

    fun recoverInterrupted() {
        jdbc.update(
            "UPDATE NOTIFICATION_INBOX SET EXECUTION_STATE = 'INTERRUPTED' " +
                    "WHERE EXECUTION_STATE = 'RUNNING' AND EXECUTION_BOOT <> :boot", mapOf("boot" to bootId)
        )
    }

    fun cleanup(retentionDays: Int): Int {
        require(retentionDays > 0) { "Retention must be at least one day" }
        val parameters =
            mapOf("cutoff" to Instant.now().minusSeconds(retentionDays.toLong() * 86400).atOffset(ZoneOffset.UTC))
        val recipients = jdbc.queryForList(
            "SELECT DISTINCT RECIPIENT FROM NOTIFICATION_INBOX " +
                    "WHERE READ_AT < :cutoff AND EXECUTION_STATE <> 'RUNNING'", parameters, String::class.java
        ).filterNotNull().toSet()
        val count = jdbc.update(
            "DELETE FROM NOTIFICATION_INBOX WHERE READ_AT < :cutoff AND EXECUTION_STATE <> 'RUNNING'",
            parameters
        )
        removeOrphans()
        events.changed(recipients)
        return count
    }

    internal fun producerState(key: String): Map<String, Any?> = jdbc.queryForMap(
        "SELECT * FROM NOTIFICATION_PRODUCER_STATE WHERE ID = :id FOR UPDATE", mapOf("id" to key)
    )

    internal fun setProducerState(key: String, version: String? = null) {
        jdbc.update(
            "UPDATE NOTIFICATION_PRODUCER_STATE SET LAST_VERSION = :version, BOOT_ID = :boot WHERE ID = :id",
            mapOf("id" to key, "version" to version, "boot" to bootId)
        )
    }

    fun invalidatePlugin(pluginId: String) {
        events.changed(
            jdbc.queryForList(
                "SELECT DISTINCT i.RECIPIENT FROM NOTIFICATION_INBOX i JOIN APP_NOTIFICATION n " +
                        "ON n.ID = i.NOTIFICATION_ID WHERE n.SOURCE = :source",
                mapOf("source" to "plugin:$pluginId"),
                String::class.java
            ).filterNotNull().toSet()
        )
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun userChanged(event: UserStateChanged) {
        if (event.update.user == null) removeOrphans()
    }

    private fun removeOrphans() {
        jdbc.update(
            "DELETE FROM APP_NOTIFICATION n WHERE NOT EXISTS (SELECT 1 FROM NOTIFICATION_INBOX i WHERE i.NOTIFICATION_ID = n.ID)",
            emptyMap<String, Any>()
        )
    }

    private fun row(rs: ResultSet) = InboxEntry(
        rs.getString("ID"),
        rs.getString("NOTIFICATION_ID"),
        rs.getString("RECIPIENT"),
        rs.getString("SOURCE"),
        rs.getString("TITLE"),
        rs.getString("BODY"),
        NotificationSeverity.valueOf(rs.getString("SEVERITY")),
        mapper.readValue(rs.getString("ACTIONS"), Array<NotificationAction>::class.java).toList(),
        rs.getObject("CREATED_AT", OffsetDateTime::class.java).toInstant(),
        rs.getObject("READ_AT", OffsetDateTime::class.java)?.toInstant(),
        rs.getString("EXECUTION_ID"),
        rs.getString("EXECUTION_STATE"),
        rs.getString("EXECUTION_ACTION")
    )

    companion object {
        private fun now() = OffsetDateTime.now(ZoneOffset.UTC)

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
