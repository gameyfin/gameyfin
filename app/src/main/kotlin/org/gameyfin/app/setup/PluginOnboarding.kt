package org.gameyfin.app.setup

import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.RolesAllowed
import jakarta.persistence.Entity
import jakarta.persistence.Id
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.plugins.management.PluginsLoadedIndicator
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Absence means an installation predates optional plugin onboarding. */
@Entity
class PluginOnboardingState(
    @Id var id: Int = 1,
    var pending: Boolean = true
)

interface PluginOnboardingRepository : JpaRepository<PluginOnboardingState, Int>

@Service
class PluginOnboardingService(private val repository: PluginOnboardingRepository) {
    fun isPending(): Boolean = repository.findByIdOrNull(1)?.pending == true

    @Transactional
    fun begin() {
        repository.save(PluginOnboardingState())
    }

    @Transactional
    fun complete() {
        repository.findByIdOrNull(1)?.let {
            it.pending = false
            repository.save(it)
        }
    }
}

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class PluginOnboardingEndpoint(
    private val onboarding: PluginOnboardingService,
    private val plugins: PluginsLoadedIndicator
) {
    fun isPluginDiscoveryComplete(): Boolean = plugins.isReady()
    fun isPending(): Boolean = onboarding.isPending()
    fun complete() = onboarding.complete()
}
