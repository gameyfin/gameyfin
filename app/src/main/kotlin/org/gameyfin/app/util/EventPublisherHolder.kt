package org.gameyfin.app.util

import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.context.ApplicationEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component

@Component
object EventPublisherHolder : ApplicationContextAware {
    private var publisher: ApplicationEventPublisher? = null

    override fun setApplicationContext(context: ApplicationContext) {
        publisher = context
    }

    fun publish(event: ApplicationEvent) {
        publisher?.publishEvent(event)
    }
}

