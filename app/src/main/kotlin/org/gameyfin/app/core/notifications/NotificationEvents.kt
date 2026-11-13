package org.gameyfin.app.core.notifications

import org.gameyfin.app.core.sync.EntityEventBroadcaster
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

@Component
class NotificationEvents {
    private val broadcaster = EntityEventBroadcaster<Set<String>>("notifications")

    internal fun subscribe(actor: NotificationActor) = broadcaster.subscribe()
        .filter { batch -> batch.any { recipients -> recipients.any { it in actor.recipients } } }
        .map { listOf(NotificationChange()) }

    fun changed(recipients: Set<String>) {
        if (recipients.isEmpty()) return
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = broadcaster.emit(recipients)
            })
        } else broadcaster.emit(recipients)
    }
}
