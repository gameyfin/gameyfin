package org.gameyfin.app.core.security

import com.vaadin.flow.server.auth.NavigationAccessControl
import com.vaadin.flow.spring.security.AuthenticationContext
import com.vaadin.flow.spring.security.RequestUtil
import com.vaadin.flow.spring.security.VaadinDefaultRequestCache
import com.vaadin.hilla.route.RouteUtil
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.DispatcherType
import jakarta.servlet.RequestDispatcher
import org.gameyfin.app.config.ConfigProperties
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.users.persistence.UserRepository
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.access.hierarchicalroles.RoleHierarchy
import org.springframework.security.core.session.SessionRegistry
import org.springframework.security.core.session.SessionRegistryImpl
import org.springframework.security.web.FilterChainProxy
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecurityErrorDispatchTest {
    @Configuration(proxyBeanMethods = false)
    @Import(SecurityConfig::class)
    class Config {
        @Bean fun config(): ConfigService = mockk<ConfigService>().also {
            every { it.get(ConfigProperties.SSO.OIDC.Enabled) } returns false
            every { it.get(ConfigProperties.Security.AllowPublicAccess) } returns false
        }
        @Bean fun ssoHandler(): SsoAuthenticationSuccessHandler = mockk()
        @Bean fun sessionRegistry(): SessionRegistry = SessionRegistryImpl()
        @Bean fun users(): UserRepository = mockk()
        @Bean fun roleHierarchy(): RoleHierarchy = mockk()
    }

    // Register UI collaborators as ready instances so Spring does not autowire their internals.
    private val runner = WebApplicationContextRunner().withUserConfiguration(Config::class.java).withInitializer { context ->
        context.beanFactory.registerSingleton("routeUtil", mockk<RouteUtil>().also {
            every { it.isRouteAllowed(any()) } returns false
        })
        context.beanFactory.registerSingleton("requestUtil", mockk<RequestUtil>(relaxed = true).also {
            every { it.urlMapping } returns "/*"
            every { it.applyUrlMapping(any<String>()) } answers { firstArg<String>().ifEmpty { "/" } }
        })
        context.beanFactory.registerSingleton("authenticationContext", mockk<AuthenticationContext>(relaxed = true))
        context.beanFactory.registerSingleton("navigationAccessControl", mockk<NavigationAccessControl>(relaxed = true))
        context.beanFactory.registerSingleton("requestCache", mockk<VaadinDefaultRequestCache>(relaxed = true).also {
            every { it.getMatchingRequest(any(), any()) } returns null
        })
    }

    @Test
    fun `error dispatch after a committed download reaches error handler without security exception`() {
        runner.run { context ->
            val security = context.getBean(FilterChainProxy::class.java)
            for (originalPath in listOf("/download/1", "/v1/games/1/download")) {
                val request = MockHttpServletRequest("GET", "/error").apply {
                    dispatcherType = DispatcherType.ERROR
                    setAttribute(RequestDispatcher.ERROR_REQUEST_URI, originalPath)
                }
                val response = MockHttpServletResponse().apply {
                    outputStream.write(byteArrayOf(1, 2, 3))
                    flushBuffer()
                }
                var reachedErrorHandler = false
                security.doFilter(request, response) { _, _ -> reachedErrorHandler = true }
                assertTrue(reachedErrorHandler)
                assertTrue(response.isCommitted)
                assertEquals(3, response.contentAsByteArray.size)
            }
        }
    }

    @Test
    fun `ordinary requests remain protected and public download setting is respected`() {
        runner.run { context ->
            val security = context.getBean(FilterChainProxy::class.java)
            for (path in listOf("/error", "/download/1")) {
                var reachedHandler = false
                security.doFilter(MockHttpServletRequest("GET", path), MockHttpServletResponse()) { _, _ ->
                    reachedHandler = true
                }
                assertFalse(reachedHandler, "$path must not bypass authorization")
            }

            every { context.getBean(ConfigService::class.java).get(ConfigProperties.Security.AllowPublicAccess) } returns true
            var reachedDownload = false
            security.doFilter(MockHttpServletRequest("GET", "/download/1"), MockHttpServletResponse()) { _, _ ->
                reachedDownload = true
            }
            assertTrue(reachedDownload)
        }
    }
}
