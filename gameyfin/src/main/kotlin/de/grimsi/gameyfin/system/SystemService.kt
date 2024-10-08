package de.grimsi.gameyfin.system

import org.springframework.cloud.context.restart.RestartEndpoint
import org.springframework.stereotype.Service

@Service
class SystemService(
    private val restartEndpoint: RestartEndpoint,
) {

    private var restartRequired = false;

    fun restart() {
        restartEndpoint.restart()
    }

    fun setRestartRequired() {
        restartRequired = true
    }

    fun isRestartRequired(): Boolean {
        return restartRequired
    }
}