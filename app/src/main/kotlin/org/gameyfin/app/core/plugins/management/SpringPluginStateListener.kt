package org.gameyfin.app.core.plugins.management

import org.pf4j.PluginStateEvent
import org.pf4j.PluginStateListener
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service

@Service
class SpringPluginStateListener(
    private val eventPublisher: ApplicationEventPublisher
) : PluginStateListener {
    override fun pluginStateChanged(event: PluginStateEvent?) {
        if (event is PluginStateEvent) {
            eventPublisher.publishEvent(event)
        }
    }
}