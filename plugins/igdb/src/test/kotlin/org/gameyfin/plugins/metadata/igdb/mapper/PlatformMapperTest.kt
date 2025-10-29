package org.gameyfin.plugins.metadata.igdb.mapper

import org.gameyfin.pluginapi.gamemetadata.Platform
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Unit tests for the PlatformMapper to ensure complete bidirectional mapping coverage
 * between IGDB platform slugs and Gameyfin Platform enums.
 *
 * Goal: 100% 1:1 mapping in both directions
 */
class PlatformMapperTest {

    @Test
    fun `all Platform enums should map to an IGDB slug`() {
        val unmappedPlatforms = mutableListOf<Platform>()

        Platform.entries.forEach { platform ->
            try {
                PlatformMapper.toIgdb(platform)
            } catch (_: IllegalArgumentException) {
                unmappedPlatforms.add(platform)
            }
        }

        assertEquals(
            emptyList(),
            unmappedPlatforms,
            "The following Platform enum entries do not have IGDB slug mappings: $unmappedPlatforms"
        )
    }

    @Test
    fun `all mapped IGDB platform slugs should map to a Platform`() {
        val allMappedIgdbSlugs = PlatformMapper.getAllMappedIgdbSlugs()
        val unmappedIgdbSlugs = mutableListOf<String>()

        allMappedIgdbSlugs.forEach { igdbSlug ->
            try {
                PlatformMapper.toGameyfin(igdbSlug)
            } catch (_: IllegalArgumentException) {
                unmappedIgdbSlugs.add(igdbSlug)
            }
        }

        assertEquals(
            emptyList(),
            unmappedIgdbSlugs,
            "The following IGDB slugs from the mapper do not have Platform mappings: $unmappedIgdbSlugs"
        )
    }

    @Test
    fun `bidirectional mapping should be consistent`() {
        val inconsistencies = mutableListOf<String>()

        Platform.entries.forEach { platform ->
            val igdbSlug = PlatformMapper.toIgdb(platform)
            assertNotNull(igdbSlug, "Platform $platform should map to an IGDB slug")

            val mappedBack = PlatformMapper.toGameyfin(igdbSlug)
            if (mappedBack != platform) {
                inconsistencies.add(
                    "Platform.$platform -> IGDB slug '$igdbSlug' -> Platform.$mappedBack (expected Platform.$platform)"
                )
            }
        }

        assertEquals(
            emptyList(),
            inconsistencies,
            "Bidirectional mapping inconsistencies found:\n${inconsistencies.joinToString("\n")}"
        )
    }

    @Test
    fun `toGameyfin collection should map all slugs`() {
        val testSlugs = setOf("ps4--1", "xboxone", "switch") // PS4, Xbox One, Nintendo Switch
        val platforms = PlatformMapper.toGameyfin(testSlugs)

        assertEquals(
            setOf(Platform.PLAYSTATION_4, Platform.XBOX_ONE, Platform.NINTENDO_SWITCH),
            platforms,
            "Should map all provided IGDB slugs to platforms"
        )
    }

    @Test
    fun `toIgdb collection should map all platforms`() {
        val platformSet = setOf(
            Platform.PLAYSTATION_4,
            Platform.XBOX_ONE,
            Platform.NINTENDO_SWITCH,
            Platform.PC_MICROSOFT_WINDOWS
        )
        val igdbSlugs = PlatformMapper.toIgdb(platformSet)

        assertEquals(
            setOf("ps4--1", "xboxone", "switch", "win"),
            igdbSlugs,
            "Should map all platforms to their IGDB slugs"
        )
    }

    @Test
    fun `specific platform mappings should be correct`() {
        assertAll(
            // Modern consoles
            { assertEquals(Platform.PLAYSTATION_5, PlatformMapper.toGameyfin("ps5")) },
            { assertEquals("ps5", PlatformMapper.toIgdb(Platform.PLAYSTATION_5)) },

            { assertEquals(Platform.XBOX_SERIES_X_S, PlatformMapper.toGameyfin("series-x-s")) },
            { assertEquals("series-x-s", PlatformMapper.toIgdb(Platform.XBOX_SERIES_X_S)) },

            { assertEquals(Platform.NINTENDO_SWITCH, PlatformMapper.toGameyfin("switch")) },
            { assertEquals("switch", PlatformMapper.toIgdb(Platform.NINTENDO_SWITCH)) },

            // PC platforms
            { assertEquals(Platform.PC_MICROSOFT_WINDOWS, PlatformMapper.toGameyfin("win")) },
            { assertEquals("win", PlatformMapper.toIgdb(Platform.PC_MICROSOFT_WINDOWS)) },

            { assertEquals(Platform.LINUX, PlatformMapper.toGameyfin("linux")) },
            { assertEquals("linux", PlatformMapper.toIgdb(Platform.LINUX)) },

            { assertEquals(Platform.MAC, PlatformMapper.toGameyfin("mac")) },
            { assertEquals("mac", PlatformMapper.toIgdb(Platform.MAC)) },

            // Classic consoles
            { assertEquals(Platform.NINTENDO_ENTERTAINMENT_SYSTEM, PlatformMapper.toGameyfin("nes")) },
            { assertEquals("nes", PlatformMapper.toIgdb(Platform.NINTENDO_ENTERTAINMENT_SYSTEM)) },

            { assertEquals(Platform.SUPER_NINTENDO_ENTERTAINMENT_SYSTEM, PlatformMapper.toGameyfin("snes")) },
            { assertEquals("snes", PlatformMapper.toIgdb(Platform.SUPER_NINTENDO_ENTERTAINMENT_SYSTEM)) },

            // Mobile
            { assertEquals(Platform.IOS, PlatformMapper.toGameyfin("ios")) },
            { assertEquals("ios", PlatformMapper.toIgdb(Platform.IOS)) },

            { assertEquals(Platform.ANDROID, PlatformMapper.toGameyfin("android")) },
            { assertEquals("android", PlatformMapper.toIgdb(Platform.ANDROID)) },

            // VR
            { assertEquals(Platform.PLAYSTATION_VR2, PlatformMapper.toGameyfin("psvr2")) },
            { assertEquals("psvr2", PlatformMapper.toIgdb(Platform.PLAYSTATION_VR2)) },

            { assertEquals(Platform.META_QUEST_3, PlatformMapper.toGameyfin("meta-quest-3")) },
            { assertEquals("meta-quest-3", PlatformMapper.toIgdb(Platform.META_QUEST_3)) }
        )
    }

    @Test
    fun `no duplicate mappings should exist`() {
        // Get all mapped IGDB slugs from all Platform enums
        val allMappedIgdbSlugs = Platform.entries.map { PlatformMapper.toIgdb(it) }
        val uniqueIgdbSlugs = allMappedIgdbSlugs.toSet()

        assertEquals(
            allMappedIgdbSlugs.size,
            uniqueIgdbSlugs.size,
            "Duplicate IGDB slug mappings found. Each Platform should map to a unique IGDB slug."
        )

        // Get all mapped Platforms from all IGDB slugs
        val allIgdbSlugs = PlatformMapper.getAllMappedIgdbSlugs()
        val allMappedPlatforms = allIgdbSlugs.map { PlatformMapper.toGameyfin(it) }
        val uniquePlatforms = allMappedPlatforms.toSet()

        assertEquals(
            allMappedPlatforms.size,
            uniquePlatforms.size,
            "Duplicate Platform mappings found. Each IGDB slug should map to a unique Platform."
        )
    }

    @Test
    fun `mapped IGDB slugs count should equal Platform enum count for 1-to-1 mapping`() {
        val allMappedIgdbSlugs = PlatformMapper.getAllMappedIgdbSlugs()

        assertEquals(
            Platform.entries.size,
            allMappedIgdbSlugs.size,
            "For 1:1 mapping, the number of mapped IGDB slugs (${allMappedIgdbSlugs.size}) should equal the number of Platform enums (${Platform.entries.size})"
        )
    }
}

