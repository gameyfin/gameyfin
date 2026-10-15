package org.gameyfin.app.setup

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import com.vaadin.hilla.exception.EndpointException
import org.gameyfin.app.core.backup.BackupService
import org.gameyfin.app.core.backup.RestoreStatusDto
import org.gameyfin.app.users.dto.ExtendedUserInfoDto
import org.gameyfin.app.users.dto.UserRegistrationDto

@Endpoint
class SetupEndpoint(
    private val setupService: SetupService,
    private val backupService: BackupService
) {
    @AnonymousAllowed
    fun isSetupCompleted(): Boolean {
        return setupService.isSetupCompleted()
    }

    @AnonymousAllowed
    fun registerSuperAdmin(superAdminRegistration: UserRegistrationDto): ExtendedUserInfoDto {
        if (setupService.isSetupCompleted()) throw EndpointException("Setup already completed")
        return setupService.createInitialAdminUser(superAdminRegistration)
    }

    /**
     * Returns the result of the last backup restore, as long as the setup has not been completed
     * (i.e. to report a failed restore triggered from the setup view).
     */
    @AnonymousAllowed
    fun getLastRestoreStatus(): RestoreStatusDto? {
        if (setupService.isSetupCompleted()) return null
        return backupService.getLastRestoreStatus()
    }
}
