package org.gameyfin.app.core.notifications

import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.PermitAll
import org.gameyfin.app.core.Role
import org.gameyfin.app.users.UserEvents
import org.gameyfin.pluginapi.notifications.NotificationSeverity
import reactor.core.publisher.Flux

@Endpoint
@PermitAll
class NotificationEndpoint(
    private val service: NotificationService, private val events: NotificationEvents,
    private val users: UserEvents
) {
    fun getInbox(archived: Boolean, page: Int, size: Int, severity: NotificationSeverity? = null): NotificationPageDto =
        service.page(archived, page, size, severity)

    fun markRead(ids: Set<String>) = service.markRead(ids)
    fun markAllRead() = service.markRead(null)
    fun deleteArchived(ids: Set<String>) = service.deleteArchived(ids)
    fun deleteAllArchived() = service.deleteArchived(null)
    fun executeAction(id: String, actionKey: String) = service.execute(id, actionKey)

    fun subscribe(): Flux<List<NotificationChange>> {
        val actor = service.actor()
        return events.subscribe(actor).takeUntilOther(users.changes().filter {
            it.id == actor.id && (it.user?.enabled != true ||
                    it.user.roles.any { role -> role == Role.ADMIN || role == Role.SUPERADMIN } != actor.admin)
        })
    }
}
