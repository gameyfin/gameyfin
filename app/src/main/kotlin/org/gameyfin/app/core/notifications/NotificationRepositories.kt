package org.gameyfin.app.core.notifications

import jakarta.persistence.*
import org.gameyfin.pluginapi.notifications.NotificationSeverity
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

@Entity
class AppNotification(
    @Id var id: String,
    var source: String,
    var title: String,
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) var body: String,
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) var severity: NotificationSeverity,
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) var actions: String,
    var createdAt: Instant
)

@Entity
class NotificationInbox(
    @Id var id: String,
    @ManyToOne(fetch = FetchType.LAZY, optional = false) var notification: AppNotification,
    var recipient: String,
    var userId: Long?,
    var executionId: String,
    var executionState: String = "READY",
    var readAt: Instant? = null,
    var executionAction: String? = null,
    var executionBoot: String? = null
)

@Entity
class NotificationProducerState(@Id var id: String, var bootId: String?, var lastVersion: String?)

@Entity
class PluginUpdateNotice(
    @Id var pluginId: String,
    @JdbcTypeCode(SqlTypes.LONG32VARCHAR) var version: String,
    var notificationId: String
)

interface NotificationRepository : JpaRepository<AppNotification, String> {
    @Modifying
    @Query("delete from AppNotification n where n.source = :source and (:id is null or n.id = :id)")
    fun resolve(source: String, id: String?)

    @Modifying
    @Query("delete from AppNotification n where not exists (select i.id from NotificationInbox i where i.notification = n)")
    fun removeOrphans()
}

private const val ARCHIVE_FILTER =
    "((:archived = true and i.readAt is not null) or (:archived = false and i.readAt is null))"
private const val PAGE_FILTER = "i.recipient in :recipients and " + ARCHIVE_FILTER +
        " and (:severity is null or i.notification.severity = :severity)"
private const val EXPIRED = "i.readAt < :cutoff and i.executionState <> 'RUNNING'"

interface NotificationInboxRepository : JpaRepository<NotificationInbox, String> {
    @Query(
        "select i from NotificationInbox i join fetch i.notification where " + PAGE_FILTER +
                " order by i.notification.createdAt desc, i.id desc"
    )
    fun page(
        recipients: List<String>,
        archived: Boolean,
        severity: NotificationSeverity?,
        pageable: Pageable
    ): List<NotificationInbox>

    @Query("select count(i) from NotificationInbox i where " + PAGE_FILTER)
    fun countVisible(recipients: List<String>, archived: Boolean, severity: NotificationSeverity?): Long

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from NotificationInbox i where i.recipient in :recipients and i.id in :ids order by i.id")
    fun lockSelected(recipients: List<String>, ids: Set<String>): List<NotificationInbox>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        "select i from NotificationInbox i where i.recipient in :recipients and " + ARCHIVE_FILTER +
                " and i.executionState <> 'RUNNING' order by i.id"
    )
    fun lockAllEligible(recipients: List<String>, archived: Boolean): List<NotificationInbox>

    fun findByIdAndRecipientIn(id: String, recipients: List<String>): NotificationInbox?

    @Query("select distinct i.recipient from NotificationInbox i where i.notification.source = :source and (:id is null or i.notification.id = :id)")
    fun recipients(source: String, id: String?): Set<String>

    @Modifying
    @Query("update NotificationInbox i set i.executionState = 'INTERRUPTED' where i.executionState = 'RUNNING' and i.executionBoot <> :boot")
    fun recoverInterrupted(boot: String)

    @Modifying
    @Query(
        """update NotificationInbox i set i.executionState = :state, i.readAt = coalesce(i.readAt, :readAt)
        where i.id = :id and i.executionState = 'RUNNING' and i.executionBoot = :boot"""
    )
    fun finish(id: String, boot: String, state: String, readAt: Instant?)

    @Query("select distinct i.recipient from NotificationInbox i where " + EXPIRED)
    fun expiredRecipients(cutoff: Instant): Set<String>

    @Modifying
    @Query("delete from NotificationInbox i where " + EXPIRED)
    fun deleteExpired(cutoff: Instant): Int
}

interface NotificationProducerRepository : JpaRepository<NotificationProducerState, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from NotificationProducerState p where p.id = :id")
    fun lock(id: String): NotificationProducerState
}

interface PluginUpdateNoticeRepository : JpaRepository<PluginUpdateNotice, String>
