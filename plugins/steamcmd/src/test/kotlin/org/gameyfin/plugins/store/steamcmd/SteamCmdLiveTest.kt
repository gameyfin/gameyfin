package org.gameyfin.plugins.store.steamcmd

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertTrue

/** Checks that api.steamcmd.net still answers in the format the plugin expects. Only runs if GAMEYFIN_LIVE_TESTS=true. */
@EnabledIfEnvironmentVariable(named = "GAMEYFIN_LIVE_TESTS", matches = "true")
class SteamCmdLiveTest {

    @Test
    fun `returns branches for a well-known app`() {
        // The Witcher 3: Wild Hunt
        val versions = SteamCmdProvider().fetchVersions("292030")
        assertTrue(versions.isNotEmpty())
        assertTrue(versions.all { it.buildId.isNotBlank() })
    }
}
