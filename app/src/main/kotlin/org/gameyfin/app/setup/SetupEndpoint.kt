package org.gameyfin.app.setup

import com.vaadin.flow.server.auth.AnonymousAllowed
import com.vaadin.hilla.Endpoint
import com.vaadin.hilla.exception.EndpointException
import org.gameyfin.app.core.backup.BackupService
import org.gameyfin.app.core.backup.RestoreStatusDto
import org.gameyfin.app.core.database.DatabaseConnectionDto
import org.gameyfin.app.core.database.DatabaseConnectionTestResultDto
import org.gameyfin.app.core.database.DatabaseInfoDto
import org.gameyfin.app.core.database.DatabaseService
import org.gameyfin.app.users.dto.ExtendedUserInfoDto
import org.gameyfin.app.users.dto.UserRegistrationDto

@Endpoint
class SetupEndpoint(
    private val setupService: SetupService,
    private val backupService: BackupService,
    private val databaseService: DatabaseService
) {
    @AnonymousAllowed
    fun isSetupCompleted(): Boolean {
        return setupService.isSetupCompleted()
    }

    /**
     * Creates the initial admin user.
     *
     * @param database if set and different from the current database, Gameyfin switches to this database
     * (transferring all data including the new user) and restarts
     */
    @AnonymousAllowed
    fun registerSuperAdmin(
        superAdminRegistration: UserRegistrationDto,
        database: DatabaseConnectionDto?
    ): ExtendedUserInfoDto {
        if (setupService.isSetupCompleted()) throw EndpointException("Setup already completed")
        // Validate the database before creating the user, so the setup can be retried if it is not reachable
        val targetDatabase = database?.let { databaseService.prepareSwitch(it) }
        val user = setupService.createInitialAdminUser(superAdminRegistration)
        targetDatabase?.let { databaseService.performSwitch(it) }
        return user
    }

    @AnonymousAllowed
    fun getDatabaseInfo(): DatabaseInfoDto? {
        if (setupService.isSetupCompleted()) return null
        return databaseService.getDatabaseInfo()
    }

    @AnonymousAllowed
    fun testDatabaseConnection(connection: DatabaseConnectionDto): DatabaseConnectionTestResultDto {
        if (setupService.isSetupCompleted()) throw EndpointException("Setup already completed")
        return databaseService.testConnection(connection)
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
