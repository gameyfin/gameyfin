package org.gameyfin.app.core.security

import com.vaadin.hilla.EndpointInvocationException.EndpointUnauthorizedException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.core.exceptions.EndpointExceptionHandler
import org.gameyfin.app.games.GameEndpoint
import org.gameyfin.app.games.GameService
import org.gameyfin.app.users.UserEvents
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertEquals

class DynamicEndpointAccessTest {
    private val config = mockk<ConfigService>()
    private val games = mockk<GameService>()
    private val endpoint = AspectJProxyFactory(GameEndpoint(games, mockk())).apply {
        addAspect(LiveSubscriptionSecurity(mockk<UserEvents>(), config))
    }.getProxy<GameEndpoint>()

    @AfterEach
    fun clearAuthentication() = SecurityContextHolder.clearContext()

    @Test
    fun `private catalog rejects absent and anonymous authentication before reading data`() {
        every { config.get(ConfigProperties.Security.AllowPublicAccess) } returns false
        assertFailsWith<AccessDeniedException> { endpoint.getById(1) }
        SecurityContextHolder.getContext().authentication = AnonymousAuthenticationToken(
            "test", "custom-anonymous-principal", listOf(SimpleGrantedAuthority("ROLE_ANONYMOUS"))
        )
        assertFailsWith<AccessDeniedException> { endpoint.getById(1) }
        verify(exactly = 0) { games.getDtoById(any()) }
    }

    @Test
    fun `dynamic access denial passes through endpoint exception handling as 401`() {
        every { config.get(ConfigProperties.Security.AllowPublicAccess) } returns false
        val handled = AspectJProxyFactory(GameEndpoint(games, mockk())).apply {
            addAspect(EndpointExceptionHandler())
            addAspect(LiveSubscriptionSecurity(mockk<UserEvents>(), config))
        }.getProxy<GameEndpoint>()
        val exception = assertFailsWith<EndpointUnauthorizedException> { handled.sync(null) }
        assertEquals(401, exception.httpStatusCode)
        verify(exactly = 0) { games.sync(any(), any()) }
    }

    @Test
    fun `public setting is rechecked on every synchronous call`() {
        every { config.get(ConfigProperties.Security.AllowPublicAccess) } returns true
        every { games.getDtoById(1) } returns null
        assertNull(endpoint.getById(1))
        every { config.get(ConfigProperties.Security.AllowPublicAccess) } returns false
        assertFailsWith<AccessDeniedException> { endpoint.getById(1) }
        verify(exactly = 1) { games.getDtoById(1) }
    }

    @Test
    fun `authenticated users can read a private catalog`() {
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
            "user", "unused", listOf(SimpleGrantedAuthority("ROLE_USER"))
        )
        every { games.getDtoById(1) } returns null
        assertNull(endpoint.getById(1))
    }

    private inline fun <reified T : Any> guarded(target: T): T = AspectJProxyFactory(target).apply {
        addAspect(LiveSubscriptionSecurity(mockk<UserEvents>(), config))
    }.getProxy()

    @Test
    fun `all dynamic Hilla endpoint classes reject anonymous calls including subscriptions and writes`() {
        every { config.get(ConfigProperties.Security.AllowPublicAccess) } returns false
        val collections = guarded(org.gameyfin.app.collections.CollectionEndpoint(mockk(), mockk()))
        val libraries = guarded(org.gameyfin.app.libraries.LibraryEndpoint(mockk(), mockk(), mockk()))
        val platforms = guarded(org.gameyfin.app.platforms.PlatformEndpoint(mockk()))
        val providers = guarded(org.gameyfin.app.core.download.provider.DownloadProviderEndpoint(mockk()))
        val plugins = guarded(org.gameyfin.app.core.plugins.PluginEndpoint(mockk()))
        val requests = guarded(org.gameyfin.app.requests.GameRequestEndpoint(mockk()))
        val settings = guarded(org.gameyfin.app.config.ConfigEndpoint(config))
        val calls: List<() -> Any?> = listOf(
            { endpoint.sync(null) }, { endpoint.getByIds(emptyList()) }, { endpoint.subscribe() },
            { collections.sync(null) }, { collections.getById(1) }, { collections.subscribeToCollectionEvents() },
            { libraries.sync(null) }, { libraries.subscribeToLibraryEvents() },
            { platforms.getStats() }, { platforms.subscribe() },
            { providers.getProviders() }, { plugins.getAll() }, { plugins.subscribe() },
            { requests.getAll() }, { requests.subscribe() }, { requests.create(mockk()) },
            { settings.areGameRequestsEnabled() }, { settings.areGuestsAllowedToRequestGames() }
        )
        calls.forEachIndexed { index, call ->
            assertFailsWith<AccessDeniedException>("Dynamic endpoint call $index must be denied") { call() }
        }
    }

    @Test
    fun `method level dynamic access permits public configuration reads`() {
        every { config.get(ConfigProperties.Security.AllowPublicAccess) } returns true
        every { config.get(ConfigProperties.Requests.Games.Enabled) } returns true
        val settings = guarded(org.gameyfin.app.config.ConfigEndpoint(config))
        kotlin.test.assertTrue(settings.areGameRequestsEnabled())
    }
}

