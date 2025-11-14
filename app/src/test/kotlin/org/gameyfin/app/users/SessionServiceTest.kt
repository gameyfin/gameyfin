package org.gameyfin.app.users

import io.mockk.*
import org.gameyfin.app.users.entities.User
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.session.SessionInformation
import org.springframework.security.core.session.SessionRegistry

class SessionServiceTest {

    private lateinit var sessionRegistry: SessionRegistry
    private lateinit var sessionService: SessionService
    private lateinit var securityContext: SecurityContext
    private lateinit var authentication: Authentication

    @BeforeEach
    fun setup() {
        sessionRegistry = mockk()
        sessionService = SessionService(sessionRegistry)
        securityContext = mockk()
        authentication = mockk()

        mockkStatic(SecurityContextHolder::class)
        every { SecurityContextHolder.getContext() } returns securityContext
        every { securityContext.authentication } returns authentication
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `logoutAllSessions should expire all user sessions`() {
        val session1 = mockk<SessionInformation>(relaxed = true)
        val session2 = mockk<SessionInformation>(relaxed = true)
        val sessions = listOf(session1, session2)
        val principal = mockk<Any>()

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { authentication.principal } returns principal
        every { sessionRegistry.getAllSessions(principal, false) } returns sessions
        every { SecurityContextHolder.clearContext() } just Runs

        sessionService.logoutAllSessions()

        verify(exactly = 1) { session1.expireNow() }
        verify(exactly = 1) { session2.expireNow() }
        verify(exactly = 1) { SecurityContextHolder.clearContext() }
    }

    @Test
    fun `logoutAllSessions should handle empty session list`() {
        val principal = mockk<Any>()

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { authentication.principal } returns principal
        every { sessionRegistry.getAllSessions(principal, false) } returns emptyList()
        every { SecurityContextHolder.clearContext() } just Runs

        sessionService.logoutAllSessions()

        verify(exactly = 1) { SecurityContextHolder.clearContext() }
    }

    @Test
    fun `logoutAllSessions should handle single session`() {
        val session = mockk<SessionInformation>(relaxed = true)
        val principal = mockk<Any>()

        mockkStatic("org.gameyfin.app.core.security.SecurityUtilsKt")
        every { authentication.principal } returns principal
        every { sessionRegistry.getAllSessions(principal, false) } returns listOf(session)
        every { SecurityContextHolder.clearContext() } just Runs

        sessionService.logoutAllSessions()

        verify(exactly = 1) { session.expireNow() }
        verify(exactly = 1) { SecurityContextHolder.clearContext() }
    }

    @Test
    fun `logoutAllSessions with user should expire all sessions for given user`() {
        val user = mockk<User>()
        val session1 = mockk<SessionInformation>(relaxed = true)
        val session2 = mockk<SessionInformation>(relaxed = true)
        val session3 = mockk<SessionInformation>(relaxed = true)
        val sessions = listOf(session1, session2, session3)

        every { sessionRegistry.getAllSessions(user, false) } returns sessions

        sessionService.logoutAllSessions(user)

        verify(exactly = 1) { session1.expireNow() }
        verify(exactly = 1) { session2.expireNow() }
        verify(exactly = 1) { session3.expireNow() }
    }

    @Test
    fun `logoutAllSessions with user should handle empty session list`() {
        val user = mockk<User>()

        every { sessionRegistry.getAllSessions(user, false) } returns emptyList()

        sessionService.logoutAllSessions(user)

        verify(exactly = 1) { sessionRegistry.getAllSessions(user, false) }
    }

    @Test
    fun `logoutAllSessions with user should not clear security context`() {
        val user = mockk<User>()
        val session = mockk<SessionInformation>(relaxed = true)

        every { sessionRegistry.getAllSessions(user, false) } returns listOf(session)

        sessionService.logoutAllSessions(user)

        verify(exactly = 0) { SecurityContextHolder.clearContext() }
    }

    @Test
    fun `logoutAllSessions should handle multiple sessions with different users`() {
        val user1 = mockk<User>()
        val user2 = mockk<User>()
        val session1 = mockk<SessionInformation>(relaxed = true)
        val session2 = mockk<SessionInformation>(relaxed = true)

        every { sessionRegistry.getAllSessions(user1, false) } returns listOf(session1)
        every { sessionRegistry.getAllSessions(user2, false) } returns listOf(session2)

        sessionService.logoutAllSessions(user1)
        sessionService.logoutAllSessions(user2)

        verify(exactly = 1) { session1.expireNow() }
        verify(exactly = 1) { session2.expireNow() }
    }

    @Test
    fun `logoutAllSessions should query for non-expired sessions`() {
        val user = mockk<User>()
        val session = mockk<SessionInformation>(relaxed = true)

        every { sessionRegistry.getAllSessions(user, false) } returns listOf(session)

        sessionService.logoutAllSessions(user)

        verify(exactly = 1) { sessionRegistry.getAllSessions(user, false) }
    }
}

