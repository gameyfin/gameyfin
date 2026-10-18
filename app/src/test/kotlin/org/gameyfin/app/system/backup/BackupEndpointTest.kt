package org.gameyfin.app.system.backup

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gameyfin.app.core.backup.BackupDto
import org.gameyfin.app.core.backup.BackupEndpoint
import org.gameyfin.app.core.backup.BackupService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BackupEndpointTest {

    private val backupService = mockk<BackupService>(relaxed = true)
    private val endpoint = BackupEndpoint(backupService)

    @Test
    fun `endpoint delegates to services`() {
        every { backupService.getBackups() } returns emptyList()

        assertEquals(emptyList<BackupDto>(), endpoint.getBackups())
        endpoint.createBackup(true)
        endpoint.deleteBackup("a.zip")
        endpoint.restoreBackup("a.zip")
        endpoint.getLastRestoreStatus()

        verify { backupService.createBackup(true) }
        verify { backupService.deleteBackup("a.zip") }
        verify { backupService.restoreBackup("a.zip", createSafetyBackup = true) }
        verify { backupService.getLastRestoreStatus() }
    }
}
