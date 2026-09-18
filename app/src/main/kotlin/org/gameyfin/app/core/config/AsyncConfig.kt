package org.gameyfin.app.core.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor

private const val POOL_SIZE = 4
private const val QUEUE_CAPACITY = 200
private const val THREAD_NAME_PREFIX = "gf-async-"

@Configuration
@EnableAsync
class AsyncConfig : AsyncConfigurer {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    /**
     * Bounded executor for `@Async` listeners.
     *
     * A plain `VirtualThreadTaskExecutor` accepts an unlimited number of tasks: every
     * published event spawns another thread with no queue and no ceiling. During a
     * library scan that is a problem, because `EntityUpdateInterceptor` publishes a
     * `GameUpdatedEvent` on every flush of a dirty game, and each event holds two
     * reconstructed `Game` instances which in turn reference the `Library` entity and
     * its games collection. Until a listener consumes the event, none of that graph can
     * be collected, so the live set grows with the number of games processed — a scan
     * that fits in 512 MiB for a small library exhausts 2 GiB on a larger one.
     *
     * The scan itself is already throttled by `library.scan.max-concurrency`, but that
     * semaphore bounds the producer, not the fan-out behind it. This does the rest.
     *
     * `CallerRunsPolicy` is the important part: once the queue is full, the scan thread
     * runs the listener itself instead of handing off. That applies backpressure, so the
     * scan slows down rather than accumulating work it cannot retire.
     */
    @Bean("virtualThreadPoolTaskExecutor")
    fun threadPoolTaskExecutor(): ThreadPoolTaskExecutor {
        return ThreadPoolTaskExecutor().apply {
            corePoolSize = POOL_SIZE
            maxPoolSize = POOL_SIZE
            setQueueCapacity(QUEUE_CAPACITY)
            setThreadNamePrefix(THREAD_NAME_PREFIX)
            setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(30)
            initialize()
        }
    }

    /**
     * Hands the bounded pool to `@Async` explicitly.
     *
     * Declaring the executor as a bean is not enough. `@EnableScheduling` makes Spring
     * Boot register a `taskScheduler` bean, and a scheduler is a `TaskExecutor` too.
     * Faced with two `TaskExecutor` beans and none named `taskExecutor`,
     * `AsyncExecutionInterceptor` silently falls back to a fresh `SimpleAsyncTaskExecutor`
     * — one platform thread per call, no queue, no ceiling — and only mentions it at INFO
     * on a Spring logger this application keeps at WARN. That is what every `@Async`
     * listener has actually been running on, with or without a custom executor bean.
     *
     * An `AsyncConfigurer` bypasses that lookup altogether. The log line makes the wiring
     * verifiable from the application log instead of from a thread dump.
     */
    override fun getAsyncExecutor(): Executor {
        log.info {
            "@Async executor: $THREAD_NAME_PREFIX* (core=$POOL_SIZE, max=$POOL_SIZE, queue=$QUEUE_CAPACITY, CallerRunsPolicy)"
        }
        return threadPoolTaskExecutor()
    }
}
