package org.gameyfin.app.notifications

import io.mockk.*
import org.gameyfin.app.core.Role
import org.gameyfin.app.core.notifications.*
import org.gameyfin.app.system.SystemService
import org.gameyfin.app.users.UserEvents
import org.gameyfin.app.users.UserStateChanged
import org.gameyfin.app.users.dto.ExtendedUserInfoDto
import org.gameyfin.app.users.dto.UserEvent
import org.gameyfin.app.users.entities.User
import org.gameyfin.app.users.persistence.UserRepository
import org.gameyfin.pluginapi.notifications.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import reactor.test.StepVerifier
import java.time.Instant
import kotlin.test.assertFailsWith

class NotificationServiceTest {
    private val store = mockk<NotificationStore>(relaxed = true)
    private val users = mockk<UserRepository>()
    private val callbacks = mockk<PluginNotificationActions>()
    private val system = mockk<SystemService>(relaxed = true)
    private val service = NotificationService(store, users, callbacks, system)
    private val admin =
        User(id = 1, username = "admin", email = "admin@example.com", enabled = true, roles = listOf(Role.ADMIN))
    private val action = NotificationAction("run", "Run", NotificationActionType.PLUGIN)
    private val entry = InboxEntry(
        "inbox", "notification", "admins", "plugin:test", "Test", "Body", NotificationSeverity.INFO,
        listOf(action), Instant.now(), null, "execution", "READY", null
    )

    @BeforeEach
    fun setup() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken.authenticated("admin", "", emptyList())
        every { users.findByUsername("admin") } returns admin
        every { store.entry(any(), "inbox", any()) } returns entry
        every { store.claim(any(), "inbox", "run") } returns entry
        every { callbacks.available("plugin:test", "run", any()) } returns true
        every { callbacks.execute(any(), any(), any(), any()) } just Runs
    }

    @AfterEach
    fun cleanup() = SecurityContextHolder.clearContext()

    @Test
    fun `successful callback uses persisted context and archives its claim`() {
        service.execute("inbox", "run")
        verify {
            callbacks.execute(
                "plugin:test",
                "run",
                true,
                NotificationActionContext(1, "notification", "execution", emptyMap())
            )
        }
        verifyOrder {
            store.claim(any(), "inbox", "run"); callbacks.execute(any(), any(), any(), any()); store.finish(
            entry,
            true
        )
        }
    }

    @Test
    fun `plugin errors release failed claim and do not archive`() {
        every { callbacks.execute(any(), any(), any(), any()) } throws LinkageError("Plugin error")
        assertFailsWith<LinkageError> { service.execute("inbox", "run") }
        verify { store.finish(entry, false) }
        verify(exactly = 0) { store.finish(entry, true) }
    }

    @Test
    fun `demotion after claim cannot execute shared recipient action`() {
        every { users.findByUsername("admin") } returnsMany listOf(
            admin,
            User(id = 1, username = "admin", email = "a@example.com", enabled = true, roles = listOf(Role.USER))
        )
        assertFailsWith<IllegalStateException> { service.execute("inbox", "run") }
        verify(exactly = 0) { callbacks.execute(any(), any(), any(), any()) }
        verify { store.finish(entry, false) }
    }

    @Test
    fun `revoked authentication is rejected even for enabled account`() {
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken.unauthenticated("admin", "")
        assertFailsWith<AccessDeniedException> { service.actor() }
    }

    @Test
    fun `restart always requires admin and completed actions cannot execute again`() {
        val restart = NotificationAction("restart", "Restart", NotificationActionType.RESTART)
        every { users.findByUsername("admin") } returns User(
            id = 1,
            username = "admin",
            email = "a@example.com",
            enabled = true,
            roles = listOf(Role.USER)
        )
        every { store.entry(any(), "inbox", any()) } returns entry.copy(recipient = "user:1", actions = listOf(restart))
        assertFailsWith<AccessDeniedException> { service.execute("inbox", "restart") }
        every { users.findByUsername("admin") } returns admin
        every { store.entry(any(), "inbox", any()) } returns entry.copy(executionState = "SUCCEEDED")
        assertFailsWith<AccessDeniedException> { service.execute("inbox", "run") }
        verify(exactly = 0) { system.restart() }
    }

    @Test
    fun `plugin service binds its source and link acknowledgement does not claim execution`() {
        val request = NotificationRequest("Test", "Body")
        service.forPlugin("first").publish(request)
        service.forPlugin("first").resolve("id")
        verify { store.publish("plugin:first", request); store.resolve("plugin:first", "id") }
        every { store.entry(any(), "inbox", any()) } returns entry.copy(
            actions = listOf(
                NotificationAction("link", "Link", NotificationActionType.LINK, "https://example.com")
            )
        )
        service.execute("inbox", "link")
        verify { store.mutate(any(), setOf("inbox"), false) }
        verify(exactly = 0) { store.claim(any(), any(), any()) }
    }

    @Test
    fun `profile edits keep stream alive while demotion and deletion close it`() {
        val events = NotificationEvents()
        val userEvents = UserEvents()
        val endpoint = NotificationEndpoint(service, events, userEvents)
        val dto = ExtendedUserInfoDto(1, "admin", false, "a@example.com", true, true, false, roles = listOf(Role.ADMIN))
        StepVerifier.create(endpoint.subscribe())
            .then { userEvents.committed(UserStateChanged(UserEvent(1, dto))) }
            .then { events.changed(setOf("admins")) }
            .expectNextCount(1)
            .then { userEvents.committed(UserStateChanged(UserEvent(1, dto.copy(roles = listOf(Role.USER))))) }
            .verifyComplete()
        StepVerifier.create(endpoint.subscribe())
            .then { userEvents.committed(UserStateChanged(UserEvent(1))) }
            .verifyComplete()
    }
}
