package org.gameyfin.app.core.database

import com.vaadin.hilla.Endpoint
import jakarta.annotation.security.RolesAllowed
import org.gameyfin.app.core.Role

@Endpoint
@RolesAllowed(Role.Names.ADMIN)
class DatabaseEndpoint(
    private val databaseService: DatabaseService
) {
    fun getDatabaseInfo(): DatabaseInfoDto = databaseService.getDatabaseInfo()

    fun testConnection(connection: DatabaseConnectionDto): DatabaseConnectionTestResultDto =
        databaseService.testConnection(connection)

    /**
     * Transfers all data to the given database and restarts Gameyfin.
     */
    fun switchDatabase(connection: DatabaseConnectionDto) = databaseService.switchDatabase(connection)
}
