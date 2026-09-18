package org.gameyfin.app.core.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor


@Configuration
@EnableAsync
class AsyncConfig {

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
    fun threadPoolTaskExecutor(): Executor {
        return ThreadPoolTaskExecutor().apply {
            corePoolSize = 4
            maxPoolSize = 4
            setQueueCapacity(200)
            setThreadNamePrefix("gf-async-")
            setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
            setWaitForTasksToCompleteOnShutdown(true)
            setAwaitTerminationSeconds(30)
            initialize()
        }
    }
}
