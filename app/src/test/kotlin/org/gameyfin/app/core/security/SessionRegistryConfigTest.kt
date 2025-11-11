package org.gameyfin.app.core.security

import org.junit.jupiter.api.Test
import org.springframework.security.core.session.SessionRegistryImpl
import kotlin.test.assertNotNull
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
}

