package de.grimsi.gameyfin.setup

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import com.vaadin.hilla.exception.EndpointException
import de.grimsi.gameyfin.users.dto.UserInfoDto
import de.grimsi.gameyfin.users.dto.UserRegistrationDto

@Endpoint
class SetupEndpoint(
    private val setupService: SetupService
) {
    @AnonymousAllowed
    fun isSetupCompleted(): Boolean {
        return setupService.isSetupCompleted()
    }

    @AnonymousAllowed
    fun registerSuperAdmin(superAdminRegistration: UserRegistrationDto): UserInfoDto {
        if (setupService.isSetupCompleted()) throw EndpointException("Setup already completed")
        return setupService.createInitialAdminUser(superAdminRegistration)
    }
}