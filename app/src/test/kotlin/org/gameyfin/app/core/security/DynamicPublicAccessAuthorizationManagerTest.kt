package org.gameyfin.app.core.security

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.web.access.intercept.RequestAuthorizationContext
import java.util.function.Supplier
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DynamicPublicAccessAuthorizationManagerTest {

    private lateinit var configService: ConfigService
    private lateinit var manager: DynamicPublicAccessAuthorizationManager
    private lateinit var context: RequestAuthorizationContext

    @BeforeEach
    fun setup() {
        configService = mockk<ConfigService>()
        manager = DynamicPublicAccessAuthorizationManager(configService)
        context = mockk<RequestAuthorizationContext>()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
        clearAllMocks()
    }

    @Test
    fun `check should allow access when user is authenticated and public access is disabled`() {
        every { configService.get(ConfigProperties.Libraries.AllowPublicAccess) } returns false

        val authentication = UsernamePasswordAuthenticationToken(
            "user",
            "password",
            listOf(SimpleGrantedAuthority("ROLE_USER"))
        )
        val authSupplier = Supplier<Authentication?> { authentication }

        val decision = manager.authorize(authSupplier, context)

        assertNotNull(decision)
        assertTrue(decision.isGranted)
    }

    @Test
    fun `check should allow access when user is authenticated and public access is enabled`() {
        every { configService.get(ConfigProperties.Libraries.AllowPublicAccess) } returns true

        val authentication = UsernamePasswordAuthenticationToken(
            "user",
            "password",
            listOf(SimpleGrantedAuthority("ROLE_USER"))
        )
        val authSupplier = Supplier<Authentication?> { authentication }

        val decision = manager.authorize(authSupplier, context)

        assertNotNull(decision)
        assertTrue(decision.isGranted)
    }

    @Test
    fun `check should allow access when user is not authenticated and public access is enabled`() {
        every { configService.get(ConfigProperties.Libraries.AllowPublicAccess) } returns true

        val authentication = mockk<Authentication>()
        every { authentication.isAuthenticated } returns false
        every { authentication.principal } returns "anonymousUser"

        val authSupplier = Supplier<Authentication?> { authentication }

        val decision = manager.authorize(authSupplier, context)

        assertNotNull(decision)
        assertTrue(decision.isGranted)
    }

    @Test
    fun `check should deny access when user is not authenticated and public access is disabled`() {
        every { configService.get(ConfigProperties.Libraries.AllowPublicAccess) } returns false

        val authentication = mockk<Authentication>()
        every { authentication.isAuthenticated } returns false
        every { authentication.principal } returns "anonymousUser"

        val authSupplier = Supplier<Authentication?> { authentication }

        val decision = manager.authorize(authSupplier, context)

        assertNotNull(decision)
        assertFalse(decision.isGranted)
    }

    @Test
    fun `check should deny access when authentication is null and public access is disabled`() {
        every { configService.get(ConfigProperties.Libraries.AllowPublicAccess) } returns false

        val authSupplier = Supplier<Authentication?> { null }

        val decision = manager.authorize(authSupplier, context)

        assertNotNull(decision)
        assertFalse(decision.isGranted)
    }

    @Test
    fun `check should allow access when authentication is null and public access is enabled`() {
        every { configService.get(ConfigProperties.Libraries.AllowPublicAccess) } returns true

        val authSupplier = Supplier<Authentication?> { null }

        val decision = manager.authorize(authSupplier, context)

        assertNotNull(decision)
        assertTrue(decision.isGranted)
    }

    @Test
    fun `check should deny access when user is authenticated as anonymousUser and public access is disabled`() {
        every { configService.get(ConfigProperties.Libraries.AllowPublicAccess) } returns false

        val authentication = mockk<Authentication>()
        every { authentication.isAuthenticated } returns true
        every { authentication.principal } returns "anonymousUser"

        val authSupplier = Supplier<Authentication?> { authentication }

        val decision = manager.authorize(authSupplier, context)

        assertNotNull(decision)
        assertFalse(decision.isGranted)
    }

    @Test
    fun `check should allow access when user is authenticated as non-anonymous and public access is disabled`() {
        every { configService.get(ConfigProperties.Libraries.AllowPublicAccess) } returns false

        val authentication = mockk<Authentication>()
        every { authentication.isAuthenticated } returns true
        every { authentication.principal } returns "john.doe"

        val authSupplier = Supplier<Authentication?> { authentication }

        val decision = manager.authorize(authSupplier, context)

        assertNotNull(decision)
        assertTrue(decision.isGranted)
    }

    @Test
    fun `check should deny access when public access config is null and user not authenticated`() {
        every { configService.get(ConfigProperties.Libraries.AllowPublicAccess) } returns null

        val authentication = mockk<Authentication>()
        every { authentication.isAuthenticated } returns false
        every { authentication.principal } returns "anonymousUser"

        val authSupplier = Supplier<Authentication?> { authentication }

        val decision = manager.authorize(authSupplier, context)

        assertNotNull(decision)
        assertFalse(decision.isGranted)
    }

    @Test
    fun `check should work when supplier is null`() {
        every { configService.get(ConfigProperties.Libraries.AllowPublicAccess) } returns false

        val decision = manager.authorize(null, context)

        assertNotNull(decision)
        assertFalse(decision.isGranted)
    }

    @Test
    fun `check should work when context is null`() {
        every { configService.get(ConfigProperties.Libraries.AllowPublicAccess) } returns false

        val authentication = UsernamePasswordAuthenticationToken(
            "user",
            "password",
            listOf(SimpleGrantedAuthority("ROLE_USER"))
        )
        val authSupplier = Supplier<Authentication?> { authentication }

        val decision = manager.authorize(authSupplier, null)

        assertNotNull(decision)
        assertTrue(decision.isGranted)
    }
}