package org.gameyfin.app.system.backup

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux

class BackupEndpointTest {

    private val backupService = mockk<BackupService>(relaxed = true)
    private val imageRecoveryService = mockk<ImageRecoveryService>(relaxed = true)
    private val endpoint = BackupEndpoint(backupService, imageRecoveryService)

    @Test
    fun `endpoint delegates to services`() {
        every { backupService.getBackups() } returns emptyList()
        every { backupService.subscribeToProgressEvents() } returns Flux.empty()
        every { imageRecoveryService.subscribeToProgressEvents() } returns Flux.empty()

        assertEquals(emptyList<BackupDto>(), endpoint.getBackups())
        endpoint.createBackup(true)
        endpoint.subscribeToBackupProgressEvents()
        endpoint.subscribeToImageRecoveryEvents()
        endpoint.deleteBackup("a.zip")
        endpoint.restoreBackup("a.zip")
        endpoint.getLastRestoreStatus()

        verify { backupService.createBackup(true) }
        verify { backupService.deleteBackup("a.zip") }
        verify { backupService.restoreBackup("a.zip", createSafetyBackup = true) }
        verify { backupService.getLastRestoreStatus() }
    }
}
