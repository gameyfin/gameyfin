package org.gameyfin.app.config.entities

import jakarta.persistence.PostPersist
import jakarta.persistence.PostRemove
import jakarta.persistence.PostUpdate
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.core.events.LibraryFilesystemWatcherConfigUpdatedEvent
import org.gameyfin.app.util.EventPublisherHolder

class ConfigEntryEntityListener {
    @PostUpdate
    @PostPersist
    @PostRemove
    fun process(configEntry: ConfigEntry) {
        when (configEntry.key) {
            ConfigProperties.Libraries.Scan.EnableFilesystemWatcher.key -> {
                EventPublisherHolder.publish(
                    LibraryFilesystemWatcherConfigUpdatedEvent(
                        this,
                        configEntry.value.toBoolean()
                    )
                )
            }
        }
    }
}