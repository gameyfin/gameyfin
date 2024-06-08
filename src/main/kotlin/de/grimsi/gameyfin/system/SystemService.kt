package de.grimsi.gameyfin.system

import org.springframework.cloud.context.restart.RestartEndpoint
import org.springframework.stereotype.Service

@Service
class SystemService(
    private val restartEndpoint: RestartEndpoint,
) {
    fun restart() {
        restartEndpoint.restart()
    }
}