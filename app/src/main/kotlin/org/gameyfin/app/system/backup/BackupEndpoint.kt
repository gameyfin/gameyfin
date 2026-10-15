package org.gameyfin.app.system.backup

import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role
import reactor.core.publisher.Flux

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class BackupEndpoint(
    private val backupService: BackupService,
    private val imageRecoveryService: ImageRecoveryService
) {
    fun getBackups(): List<BackupDto> = backupService.getBackups()

    /**
     * Starts creating a backup in the background. The progress is published via [subscribeToBackupProgressEvents].
     */
    fun createBackup(includeMedia: Boolean): BackupProgress = backupService.createBackup(includeMedia)

    fun subscribeToBackupProgressEvents(): Flux<List<BackupProgress>> = backupService.subscribeToProgressEvents()

    /**
     * Progress of the recovery of images which are missing after a restore (see [ImageRecoveryService]).
     */
    fun subscribeToImageRecoveryEvents(): Flux<List<ImageRecoveryProgress>> =
        imageRecoveryService.subscribeToProgressEvents()

    fun deleteBackup(name: String) = backupService.deleteBackup(name)

    /**
     * Restores the given backup. A backup of the current database is created first, then Gameyfin restarts.
     */
    fun restoreBackup(name: String) = backupService.restoreBackup(name, createSafetyBackup = true)

    fun getLastRestoreStatus(): RestoreStatusDto? = backupService.getLastRestoreStatus()
}
