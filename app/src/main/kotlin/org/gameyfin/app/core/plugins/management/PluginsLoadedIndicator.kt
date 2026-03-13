package org.gameyfin.app.core.plugins.management

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicBoolean

@Component("pluginsLoaded")
class PluginsLoadedIndicator : HealthIndicator {

    private val ready = AtomicBoolean(false)

    fun markReady() {
        ready.set(true)
    }

    override fun health(): Health {
        return if (ready.get()) {
            Health.up().withDetail("plugins", "loaded").build()
        } else {
            Health.outOfService().withDetail("plugins", "loading").build()
        }
    }
}

