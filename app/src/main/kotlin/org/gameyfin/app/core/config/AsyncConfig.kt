package org.gameyfin.app.core.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.VirtualThreadTaskExecutor
import org.springframework.scheduling.annotation.EnableAsync
import java.util.concurrent.Executor


@Configuration
@EnableAsync
class AsyncConfig {

    @Bean("virtualThreadPoolTaskExecutor")
    fun threadPoolTaskExecutor(): Executor {
        return VirtualThreadTaskExecutor()
    }
}