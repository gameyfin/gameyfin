package org.gameyfin.app.core.backup

import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.tasks.dto.TaskRunDto

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class BackupEndpoint(
    private val backupService: BackupService
) {
    fun getBackups(): List<BackupDto> = backupService.getBackups()

    /**
     * Starts creating a backup in the background. The progress is published as run of the backup task
     * (see [org.gameyfin.app.core.tasks.TaskEndpoint.subscribeToTaskRuns]).
     */
    fun createBackup(includeMedia: Boolean): TaskRunDto = backupService.createBackup(includeMedia)

    fun deleteBackup(name: String) = backupService.deleteBackup(name)

    /**
     * Restores the given backup. A backup of the current database is created first, then Gameyfin restarts.
     */
    fun restoreBackup(name: String) = backupService.restoreBackup(name, createSafetyBackup = true)

    fun getLastRestoreStatus(): RestoreStatusDto? = backupService.getLastRestoreStatus()
}
