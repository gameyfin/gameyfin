package org.gameyfin.app.notifications

import org.gameyfin.app.core.notifications.NotificationStore
import org.gameyfin.pluginapi.notifications.*
import org.junit.jupiter.api.Test
import kotlin.test.*

class NotificationStoreValidationTest {
    private val action = NotificationAction("run", "Run", NotificationActionType.PLUGIN)
    private val request = NotificationRequest("Title", "Body", actions = listOf(action))

    @Test
    fun `valid requests accept limits and all supported action types`() {
        NotificationStore.validate(request.copy(
            title = "t".repeat(250), body = "b".repeat(100_000),
            recipients = NotificationRecipients(NotificationAudience.USERS, setOf(1)),
            actions = List(10) { index -> action.copy(
                key = "$index".padEnd(100, 'a'), label = "l".repeat(100),
                parameters = (1..50).associate { "$it".padEnd(100, 'k') to "v".repeat(10_000) }
            ) }
        ))
        NotificationStore.validate(request.copy(actions = listOf(
            action, NotificationAction("link", "Link", NotificationActionType.LINK, "/docs"),
            NotificationAction("restart", "Restart", NotificationActionType.RESTART)
        )))
    }

    @Test
    fun `invalid content recipients and action lists are rejected`() {
        val invalid = listOf(
            request.copy(title = " "), request.copy(title = "t".repeat(251)),
            request.copy(body = "b".repeat(100_001)),
            request.copy(actions = List(11) { action.copy(key = "a$it") }),
            request.copy(actions = listOf(action, action)),
            request.copy(recipients = NotificationRecipients(NotificationAudience.USERS)),
            request.copy(recipients = NotificationRecipients(NotificationAudience.ADMINS, setOf(1))),
            request.copy(recipients = NotificationRecipients(NotificationAudience.ALL_USERS, setOf(1)))
        )
        invalid.forEachIndexed { index, value ->
            assertFailsWith<IllegalArgumentException>("Invalid request $index") { NotificationStore.validate(value) }
        }
    }

    @Test
    fun `invalid action keys labels parameters and URLs are rejected`() {
        val invalid = listOf(
            action.copy(key = ""), action.copy(key = "a".repeat(101)), action.copy(key = "has space"),
            action.copy(label = " "), action.copy(label = "l".repeat(101)),
            action.copy(parameters = (1..51).associate { "$it" to "value" }),
            action.copy(parameters = mapOf("k".repeat(101) to "value")),
            action.copy(parameters = mapOf("key" to "v".repeat(10_001))),
            action.copy(url = "https://example.com"),
            action.copy(type = NotificationActionType.LINK),
            action.copy(type = NotificationActionType.LINK, url = "javascript:alert(1)")
        )
        invalid.forEachIndexed { index, value ->
            assertFailsWith<IllegalArgumentException>("Invalid action $index") {
                NotificationStore.validate(request.copy(actions = listOf(value)))
            }
        }
    }

    @Test
    fun `safe links reject credentials missing hosts malformed URIs and control characters`() {
        listOf("https://user:pass@example.com", "https:///path", "https://[", "relative/path", "", "/docs\u0000").forEach {
            assertFalse(NotificationStore.safeLink(it), it)
        }
        listOf("HTTPS://example.com/docs", "/", "/docs?tab=one#section").forEach {
            assertTrue(NotificationStore.safeLink(it), it)
        }
    }
}
