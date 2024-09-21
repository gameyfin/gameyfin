package de.grimsi.gameyfin.notifications

import com.vaadin.hilla.Endpoint
import de.grimsi.gameyfin.core.Roles
import jakarta.annotation.security.RolesAllowed

@Endpoint
@RolesAllowed(Roles.Names.SUPERADMIN, Roles.Names.ADMIN)
class NotificationEndpoint(
    private val notificationService: NotificationService
) {

    fun verifyCredentials(provider: String, credentials: Map<String, Any>): Boolean {
        return notificationService.testCredentials(provider, credentials)
    }
}