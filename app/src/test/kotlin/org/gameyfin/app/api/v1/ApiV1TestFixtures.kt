package org.gameyfin.app.api.v1

import io.mockk.every
import io.mockk.mockk
import org.gameyfin.app.api.security.ApiAccess
import org.gameyfin.app.api.security.ApiPrincipal
import org.gameyfin.app.api.security.CredentialKind
import org.gameyfin.app.core.tasks.TaskRunStatus
import org.gameyfin.app.core.tasks.dto.TaskRunDto
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.games.entities.GameMetadata
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.pluginapi.tasks.TaskTrigger
import java.time.Instant

internal fun servicePrincipal() = ApiPrincipal("cred", "Service key", CredentialKind.SERVICE, null, "api-key:cred")
internal fun personalPrincipal(userId: Long = 7) =
    ApiPrincipal("pat", "Personal token", CredentialKind.PERSONAL, userId, "owner")

internal fun allowingAccess(principal: ApiPrincipal = servicePrincipal()): ApiAccess = mockk<ApiAccess>().also {
    every { it.require(any()) } returns principal
    every { it.principal() } returns principal
    every { it.personal() } returns principal
}

internal fun testLibrary(id: Long = 1) = Library(id = id, name = "Library $id")

internal fun testGame(id: Long = 10, title: String? = "Game", library: Library = testLibrary()) =
    Game(id = id, library = library, title = title, metadata = GameMetadata("/games/$id"))

internal fun taskRun(id: String = "run-1") = TaskRunDto(
    id = id, taskKey = "deletion", taskName = "Deletion", displayName = "Delete", link = null,
    trigger = TaskTrigger.MANUAL, status = TaskRunStatus.IN_PROGRESS, step = null,
    startedAt = Instant.parse("2024-01-01T00:00:00Z"), finishedAt = null, summary = null,
    errorMessage = null, cancellable = false, cancelRequested = false
)
