package org.gameyfin.app.users

import org.gameyfin.app.users.dto.UserEvent
import org.gameyfin.app.core.security.LiveUserAuthentication
import org.springframework.stereotype.Service
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks

/** Internal event, published inside the transaction which changes the user. */
class UserStateChanged(val update: UserEvent) : org.springframework.context.ApplicationEvent(update)

@Service
class UserEvents {
    // Hilla requests unbounded demand. Do not replay old events to the first connection;
    // every new/reconnected client obtains its own current snapshot.
    private val updates = Sinks.many().multicast().directBestEffort<UserEvent>()

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Synchronized
    fun committed(event: UserStateChanged) {
        LiveUserAuthentication.update(event.update)
        updates.tryEmitNext(event.update)
    }

    fun changes(): Flux<UserEvent> = updates.asFlux()

    fun subscribe(): Flux<List<UserEvent>> = changes().map { listOf(it) }

    fun subscribeCurrentUser(id: Long): Flux<List<UserEvent>> = changes()
        .filter { it.id == id }
        .takeUntil { it.user?.enabled != true }
        .map { listOf(it) }
}
