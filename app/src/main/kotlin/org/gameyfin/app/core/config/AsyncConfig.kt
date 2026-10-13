package org.gameyfin.app.core.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.VirtualThreadTaskExecutor
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor
import java.util.concurrent.ThreadPoolExecutor

const val VIRTUAL_THREAD_EXECUTOR = "virtualThreadPoolTaskExecutor"
const val BOUNDED_EXECUTOR = "boundedTaskExecutor"

/**
 * Executors for `@Async` methods.
 *
 * The default (used by plain `@Async`) is a virtual thread executor. It is handed over explicitly via
 * [AsyncConfigurer], because with `@EnableScheduling` there are multiple `TaskExecutor` beans and Spring
 * would otherwise silently fall back to a `SimpleAsyncTaskExecutor` (one platform thread per call).
 *
 * Listeners of game events (`@Async(BOUNDED_EXECUTOR)`) must use the bounded executor instead: during a
 * library scan every dirty flush publishes a `GameUpdatedEvent` holding reconstructed `Game` graphs, and an
 * unbounded executor lets those accumulate until memory is exhausted. `CallerRunsPolicy` applies backpressure
 * by running the listener on the publishing thread once the queue is full.
 */
@Configuration
@EnableAsync
class AsyncConfig : AsyncConfigurer {

    @Bean(VIRTUAL_THREAD_EXECUTOR)
    fun virtualThreadTaskExecutor(): Executor = VirtualThreadTaskExecutor("async-virtual-")

    @Bean(BOUNDED_EXECUTOR)
    fun boundedTaskExecutor(
        @Value($$"${gameyfin.async.bounded.core-pool-size:4}") core: Int,
        @Value($$"${gameyfin.async.bounded.max-pool-size:16}") max: Int,
        @Value($$"${gameyfin.async.bounded.queue-capacity:100}") queue: Int
    ): ThreadPoolTaskExecutor {
        return ThreadPoolTaskExecutor().apply {
            corePoolSize = core
            maxPoolSize = max
            queueCapacity = queue
            setThreadNamePrefix("async-bounded-")
            setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
            setWaitForTasksToCompleteOnShutdown(true)
        }
    }

    override fun getAsyncExecutor(): Executor = virtualThreadTaskExecutor()
}
