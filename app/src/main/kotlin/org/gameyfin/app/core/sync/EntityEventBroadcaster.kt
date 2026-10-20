package org.gameyfin.app.core.sync

import io.github.oshai.kotlinlogging.KotlinLogging
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

/**
 * Multicasts entity events to websocket subscribers, buffered into batches of [bufferDuration].
 */
class EntityEventBroadcaster<T : Any>(
    private val name: String,
    private val bufferDuration: Duration = 100.milliseconds
) {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    private val sink = Sinks.many().multicast().onBackpressureBuffer<T>(1024, false)

    fun subscribe(): Flux<List<T>> {
        log.debug { "New subscription for $name" }
        return sink.asFlux()
            .buffer(bufferDuration.toJavaDuration())
            .filter { it.isNotEmpty() }
            .doOnSubscribe {
                log.debug { "Subscriber added to $name [${sink.currentSubscriberCount()}]" }
            }
            .doFinally {
                log.debug { "Subscriber removed from $name with signal type $it [${sink.currentSubscriberCount()}]" }
            }
    }

    @Synchronized
    fun emit(event: T) {
        val result = sink.tryEmitNext(event)
        if (result.isFailure && result != Sinks.EmitResult.FAIL_ZERO_SUBSCRIBER) {
            log.warn { "Failed to emit event to $name: $result" }
        }
    }
}
