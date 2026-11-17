package org.gameyfin.plugins.store.epic

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertTrue

/** Checks that api.egdata.app still answers in the format the plugin expects. Only runs if GAMEYFIN_LIVE_TESTS=true. */
@EnabledIfEnvironmentVariable(named = "GAMEYFIN_LIVE_TESTS", matches = "true")
class EpicLiveTest {

    @Test
    fun `search and versions work`() {
        JavaEpicHttp().use { http ->
            val client = EpicClient(http)
            val results = client.search("Baldur's Gate Enhanced Edition")
            assertTrue(results.isNotEmpty())
            // Mass Effect: Andromeda Deluxe Edition, one of the few EA titles that egdata has builds for
            assertTrue(client.fetchVersions("c015ff4eb31d4fa98257f9e95dbd52ba").isNotEmpty())
        }
    }
}
