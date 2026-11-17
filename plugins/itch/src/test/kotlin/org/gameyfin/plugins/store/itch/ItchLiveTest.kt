package org.gameyfin.plugins.store.itch

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import kotlin.test.Test
import kotlin.test.assertTrue

/** Checks that itch.io still answers in the format the plugin expects. Only runs if GAMEYFIN_LIVE_TESTS=true. */
@EnabledIfEnvironmentVariable(named = "GAMEYFIN_LIVE_TESTS", matches = "true")
class ItchLiveTest {

    @Test
    fun `search and downloads work`() {
        val provider = ItchProvider()
        val results = provider.search("X-Moon")
        assertTrue(results.isNotEmpty(), "no results")
        assertTrue(results.all { isValidProductId(it.id) }, "invalid IDs: ${results.map { it.id }}")
        assertTrue(provider.fetchVersions("leafo.itch.io/x-moon").isNotEmpty(), "no versions")
    }
}
