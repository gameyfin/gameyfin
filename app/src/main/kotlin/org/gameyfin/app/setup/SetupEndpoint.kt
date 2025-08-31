package org.gameyfin.app.setup

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import com.vaadin.hilla.exception.EndpointException
import org.gameyfin.app.users.dto.UserInfoAdminDto
import org.gameyfin.app.users.dto.UserRegistrationDto

@Endpoint
class SetupEndpoint(
    private val setupService: SetupService
) {
    @AnonymousAllowed
    fun isSetupCompleted(): Boolean {
        return setupService.isSetupCompleted()
    }

    @AnonymousAllowed
    fun registerSuperAdmin(superAdminRegistration: UserRegistrationDto): UserInfoAdminDto {
        if (setupService.isSetupCompleted()) throw EndpointException("Setup already completed")
        return setupService.createInitialAdminUser(superAdminRegistration)
    }
}