package org.gameyfin.app.core.logging

import io.mockk.*
import org.gameyfin.app.core.Role
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.Authentication
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.context.SecurityContextImpl
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import kotlin.test.assertNotNull

@Suppress("ReactiveStreamsUnusedPublisher")
class LogEndpointTest {

    private lateinit var logService: LogService
    private lateinit var logEndpoint: LogEndpoint

    @BeforeEach
    fun setup() {
        logService = mockk<LogService>(relaxed = true)
        logEndpoint = LogEndpoint(logService)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `reloadLogConfig should delegate to logService`() {
        logEndpoint.reloadLogConfig()

        verify(exactly = 1) { logService.configureFileLogging() }
    }

    @Test
    fun `reloadLogConfig should call configureFileLogging without parameters`() {
        every { logService.configureFileLogging() } just runs

        logEndpoint.reloadLogConfig()

        verify(exactly = 1) { logService.configureFileLogging() }
    }

    @Test
    fun `getApplicationLogs should return logs when user is admin`() {
        // Setup admin authentication
        setupAuthentication(listOf(SimpleGrantedAuthority(Role.Names.ADMIN)))

        val expectedFlux = Flux.just("log line 1", "log line 2", "log line 3")
        every { logService.streamLogs() } returns expectedFlux

        val result = logEndpoint.getApplicationLogs()

        assertNotNull(result)
        StepVerifier.create(result)
            .expectNext("log line 1")
            .expectNext("log line 2")
            .expectNext("log line 3")
            .verifyComplete()

        verify(exactly = 1) { logService.streamLogs() }
    }

    @Test
    fun `getApplicationLogs should return logs when user is superadmin`() {
        // Setup superadmin authentication
        setupAuthentication(listOf(SimpleGrantedAuthority(Role.Names.SUPERADMIN)))

        val expectedFlux = Flux.just("log line 1")
        every { logService.streamLogs() } returns expectedFlux

        val result = logEndpoint.getApplicationLogs()

        assertNotNull(result)
        StepVerifier.create(result)
            .expectNext("log line 1")
            .verifyComplete()

        verify(exactly = 1) { logService.streamLogs() }
    }

    @Test
    fun `getApplicationLogs should return empty flux when user is not admin`() {
        // Setup user authentication (non-admin)
        setupAuthentication(listOf(SimpleGrantedAuthority(Role.Names.USER)))

        val result = logEndpoint.getApplicationLogs()

        assertNotNull(result)
        StepVerifier.create(result)
            .verifyComplete()

        verify(exactly = 0) { logService.streamLogs() }
    }

    @Test
    fun `getApplicationLogs should return empty flux when user is not authenticated`() {
        // No authentication setup
        SecurityContextHolder.clearContext()

        val result = logEndpoint.getApplicationLogs()

        assertNotNull(result)
        StepVerifier.create(result)
            .verifyComplete()

        verify(exactly = 0) { logService.streamLogs() }
    }

    @Test
    fun `getApplicationLogs should return empty flux when user has no authorities`() {
        // Setup authentication with no authorities
        setupAuthentication(emptyList())

        val result = logEndpoint.getApplicationLogs()

        assertNotNull(result)
        StepVerifier.create(result)
            .verifyComplete()

        verify(exactly = 0) { logService.streamLogs() }
    }

    @Test
    fun `getApplicationLogs should return empty flux when authentication is null`() {
        val mockAuthentication = mockk<Authentication>(relaxed = true)
        val securityContext = SecurityContextImpl(mockAuthentication)
        SecurityContextHolder.setContext(securityContext)

        val result = logEndpoint.getApplicationLogs()

        assertNotNull(result)
        StepVerifier.create(result)
            .verifyComplete()

        verify(exactly = 0) { logService.streamLogs() }
    }

    @Test
    fun `getApplicationLogs should handle empty log stream for admin`() {
        // Setup admin authentication
        setupAuthentication(listOf(SimpleGrantedAuthority(Role.Names.ADMIN)))

        val expectedFlux = Flux.empty<String>()
        every { logService.streamLogs() } returns expectedFlux

        val result = logEndpoint.getApplicationLogs()

        assertNotNull(result)
        StepVerifier.create(result)
            .verifyComplete()

        verify(exactly = 1) { logService.streamLogs() }
    }

    @Test
    fun `getApplicationLogs should handle large log stream for admin`() {
        // Setup admin authentication
        setupAuthentication(listOf(SimpleGrantedAuthority(Role.Names.ADMIN)))

        val logLines = (1..100).map { "log line $it" }
        val expectedFlux = Flux.fromIterable(logLines)
        every { logService.streamLogs() } returns expectedFlux

        val result = logEndpoint.getApplicationLogs()

        assertNotNull(result)
        StepVerifier.create(result)
            .expectNextCount(100)
            .verifyComplete()

        verify(exactly = 1) { logService.streamLogs() }
    }

    @Test
    fun `getApplicationLogs should not call streamLogs for user with mixed authorities without admin`() {
        // Setup authentication with multiple roles but no admin
        setupAuthentication(
            listOf(
                SimpleGrantedAuthority(Role.Names.USER),
                SimpleGrantedAuthority("ROLE_CUSTOM")
            )
        )

        val result = logEndpoint.getApplicationLogs()

        assertNotNull(result)
        StepVerifier.create(result)
            .verifyComplete()

        verify(exactly = 0) { logService.streamLogs() }
    }

    @Test
    fun `getApplicationLogs should call streamLogs for user with mixed authorities including admin`() {
        // Setup authentication with multiple roles including admin
        setupAuthentication(
            listOf(
                SimpleGrantedAuthority(Role.Names.USER),
                SimpleGrantedAuthority(Role.Names.ADMIN)
            )
        )

        val expectedFlux = Flux.just("log line 1")
        every { logService.streamLogs() } returns expectedFlux

        val result = logEndpoint.getApplicationLogs()

        assertNotNull(result)
        StepVerifier.create(result)
            .expectNext("log line 1")
            .verifyComplete()

        verify(exactly = 1) { logService.streamLogs() }
    }

    @Test
    fun `multiple calls to getApplicationLogs should call streamLogs each time for admin`() {
        // Setup admin authentication
        setupAuthentication(listOf(SimpleGrantedAuthority(Role.Names.ADMIN)))

        val expectedFlux = Flux.just("log line")
        every { logService.streamLogs() } returns expectedFlux

        logEndpoint.getApplicationLogs()
        logEndpoint.getApplicationLogs()
        logEndpoint.getApplicationLogs()

        verify(exactly = 3) { logService.streamLogs() }
    }

    @Test
    fun `multiple calls to reloadLogConfig should call configureFileLogging each time`() {
        logEndpoint.reloadLogConfig()
        logEndpoint.reloadLogConfig()
        logEndpoint.reloadLogConfig()

        verify(exactly = 3) { logService.configureFileLogging() }
    }

    private fun setupAuthentication(authorities: List<GrantedAuthority>) {
        val authentication = mockk<Authentication>()
        every { authentication.authorities } returns authorities
        every { authentication.isAuthenticated } returns true

        val securityContext = SecurityContextImpl(authentication)
        SecurityContextHolder.setContext(securityContext)
    }
}

