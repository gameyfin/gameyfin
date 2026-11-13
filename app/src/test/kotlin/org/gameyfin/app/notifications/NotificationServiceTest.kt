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
import kotlin.test.*

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
    fun `page maps inbox fields and computes availability without exposing action parameters`() {
        val link = NotificationAction("docs", "Docs", NotificationActionType.LINK, "/docs")
        val restart = NotificationAction("restart", "Restart", NotificationActionType.RESTART)
        val archived = entry.copy(readAt = entry.createdAt, actions = listOf(action, link, restart))
        val actor = NotificationActor(1, true)
        every { store.page(actor, true, 2, 10, NotificationSeverity.INFO) } returns listOf(archived)
        every { store.count(actor, true, NotificationSeverity.INFO) } returns 12
        every { store.count(actor, false) } returns 3

        val page = service.page(true, 2, 10, NotificationSeverity.INFO)

        assertEquals(NotificationPageDto(listOf(NotificationDto(
            entry.id, entry.title, entry.body, entry.severity, entry.source, entry.createdAt,
            archived.readAt, "READY", true, listOf(
                NotificationActionDto("run", "Run", NotificationActionType.PLUGIN, null, true),
                NotificationActionDto("docs", "Docs", NotificationActionType.LINK, "/docs", true),
                NotificationActionDto("restart", "Restart", NotificationActionType.RESTART, null, true)
            )
        )), 12, 3), page)
    }

    @Test
    fun `page disables running and different retry actions but keeps links usable`() {
        val link = NotificationAction("docs", "Docs", NotificationActionType.LINK, "/docs")
        val entries = listOf(
            entry.copy(recipient = "user:1", executionState = "RUNNING", actions = listOf(action, link)),
            entry.copy(executionState = "FAILED", executionAction = "other"),
            entry.copy(actions = listOf(link.copy(url = null))),
            entry.copy(actions = listOf(link.copy(url = "javascript:alert(1)")))
        )
        every { store.page(any(), false, 0, 25, null) } returns entries
        val items = service.page(false, 0, 25).items
        assertFalse(items.first().shared)
        assertEquals(listOf(false, true), items.first().actions.map { it.available })
        assertTrue(items.drop(1).all { !it.actions.single().available })
    }

    @Test
    fun `missing anonymous disabled and deleted accounts cannot access inbox`() {
        SecurityContextHolder.clearContext()
        assertFailsWith<AccessDeniedException> { service.actor() }
        SecurityContextHolder.getContext().authentication =
            org.springframework.security.authentication.AnonymousAuthenticationToken(
                "key", "anonymous", listOf(org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ANONYMOUS")))
        assertFailsWith<AccessDeniedException> { service.actor() }
        setup()
        every { users.findByUsername("admin") } returns User(id = 1, username = "admin", email = "a@example.com", enabled = false)
        assertFailsWith<AccessDeniedException> { service.actor() }
        every { users.findByUsername("admin") } returns null
        assertFailsWith<AccessDeniedException> { service.actor() }
    }

    @Test
    fun `superadmin can restart and archive mutations use current recipient scope`() {
        every { users.findByUsername("admin") } returns User(id = 1, username = "admin", email = "a@example.com", enabled = true, roles = listOf(Role.SUPERADMIN))
        val restart = entry.copy(actions = listOf(NotificationAction("restart", "Restart", NotificationActionType.RESTART)))
        every { store.entry(any(), "inbox", any()) } returns restart
        every { store.claim(any(), "inbox", "restart") } returns restart
        service.execute("inbox", "restart")
        service.markRead(null)
        service.deleteArchived(setOf("inbox"))
        verify { system.restart(); store.finish(restart, true) }
        verify { store.mutate(NotificationActor(1, true), null, false) }
        verify { store.mutate(NotificationActor(1, true), setOf("inbox"), true) }
    }

    @Test
    fun `unknown or unavailable plugin actions cannot be claimed`() {
        assertFailsWith<IllegalArgumentException> { service.execute("inbox", "missing") }
        every { callbacks.available(any(), any(), any()) } returns false
        assertFailsWith<AccessDeniedException> { service.execute("inbox", "run") }
        verify(exactly = 0) { store.claim(any(), any(), any()) }
    }

    @Test
    fun `permission revoked after claim fails action and finish errors preserve original failure`() {
        every { callbacks.available(any(), any(), any()) } returnsMany listOf(true, false)
        val finishError = IllegalStateException("Cannot persist failure")
        every { store.finish(entry, false) } throws finishError
        val failure = assertFailsWith<IllegalStateException> { service.execute("inbox", "run") }
        assertEquals("Action unavailable", failure.message)
        assertEquals(listOf(finishError), failure.suppressed.toList())
        verify(exactly = 0) { callbacks.execute(any(), any(), any(), any()) }
    }

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
