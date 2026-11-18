package org.gameyfin.app.core.plugins.marketplace

import jakarta.persistence.Entity
import jakarta.persistence.Id
import org.springframework.data.jpa.repository.JpaRepository

@Entity
class PluginRecoveryCandidate(
    @Id var pluginId: String,
    var completed: Boolean = false,
    var lastError: String? = null
)

interface PluginRecoveryRepository : JpaRepository<PluginRecoveryCandidate, String> {
    fun findAllByCompletedFalse(): List<PluginRecoveryCandidate>
}
