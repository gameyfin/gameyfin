package org.gameyfin.app.core.security

import jakarta.servlet.http.HttpSessionEvent
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpSession
import org.springframework.mock.web.MockServletContext
import org.springframework.security.core.session.SessionRegistry
import org.springframework.security.core.session.SessionRegistryImpl
import org.springframework.security.web.session.HttpSessionEventPublisher
import org.springframework.web.context.WebApplicationContext
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionRegistryConfigTest {

    private val config = SessionRegistryConfig()

    @Test
    fun `sessionRegistry should return SessionRegistry instance`() {
        val registry = config.sessionRegistry()

        assertNotNull(registry)
    }

    @Test
    fun `sessionRegistry should return SessionRegistryImpl`() {
        val registry = config.sessionRegistry()

        assertTrue(registry is SessionRegistryImpl)
    }

    @Test
    fun `sessionRegistry should return working registry`() {
        val registry = config.sessionRegistry()

        // Verify it's initialized and can be used
        val allPrincipals = registry.allPrincipals

        assertNotNull(allPrincipals)
    }

    @Test
    fun `sessionRegistry should return empty principals list initially`() {
        val registry = config.sessionRegistry()

        val allPrincipals = registry.allPrincipals

        assertNotNull(allPrincipals)
        assertTrue(allPrincipals.isEmpty())
    }

    @Test
    fun `httpSessionEventPublisher should remove destroyed sessions from registry`() {
        val servletContext = MockServletContext()
        val context = AnnotationConfigWebApplicationContext().apply {
            register(SessionRegistryConfig::class.java)
            this.servletContext = servletContext
            refresh()
        }
        servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, context)

        context.use { context ->
            val registry = context.getBean(SessionRegistry::class.java)
            val publisher = context.getBean(HttpSessionEventPublisher::class.java)
            val session = MockHttpSession(servletContext)

            registry.registerNewSession(session.id, "user")
            assertNotNull(registry.getSessionInformation(session.id))

            publisher.sessionDestroyed(HttpSessionEvent(session))

            assertNull(registry.getSessionInformation(session.id))
            assertTrue(registry.allPrincipals.isEmpty())
        }
    }
}

