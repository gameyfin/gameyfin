package org.gameyfin.plugins.store.gogdb

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertTrue

/** Checks that gogdb.org still answers in the format the plugin expects. Only runs if GAMEYFIN_LIVE_TESTS=true. */
@EnabledIfEnvironmentVariable(named = "GAMEYFIN_LIVE_TESTS", matches = "true")
class GogDbLiveTest {

    @Test
    fun `returns builds for a well-known product`() {
        // Baldur's Gate: Enhanced Edition
        val versions = GogDbProvider().fetchVersions("1207666353")
        assertTrue(versions.isNotEmpty())
        assertTrue(versions.all { it.label.isNotBlank() })
    }
}
