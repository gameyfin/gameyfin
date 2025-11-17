package org.gameyfin.app.core.download.provider


import org.junit.jupiter.api.Test
import kotlin.test.*

class DownloadProviderDtoTest {

    @Test
    fun `should create DTO with all fields`() {
        val dto = DownloadProviderDto(
            key = "org.example.Provider",
            name = "Example Provider",
            priority = 10,
            description = "This is a test provider",
            shortDescription = "Test provider"
        )

        assertEquals("org.example.Provider", dto.key)
        assertEquals("Example Provider", dto.name)
        assertEquals(10, dto.priority)
        assertEquals("This is a test provider", dto.description)
        assertEquals("Test provider", dto.shortDescription)
    }

    @Test
    fun `should handle null shortDescription`() {
        val dto = DownloadProviderDto(
            key = "org.example.Provider",
            name = "Example Provider",
            priority = 10,
            description = "This is a test provider",
            shortDescription = null
        )

        assertNull(dto.shortDescription)
    }

    @Test
    fun `should support data class copy`() {
        val original = DownloadProviderDto(
            key = "org.example.Provider",
            name = "Example Provider",
            priority = 10,
            description = "This is a test provider",
            shortDescription = "Test provider"
        )

        val copied = original.copy(priority = 20)

        assertEquals(20, copied.priority)
        assertEquals(original.key, copied.key)
        assertEquals(original.name, copied.name)
    }

    @Test
    fun `should support equality comparison`() {
        val dto1 = DownloadProviderDto(
            key = "org.example.Provider",
            name = "Example Provider",
            priority = 10,
            description = "This is a test provider",
            shortDescription = "Test provider"
        )

        val dto2 = DownloadProviderDto(
            key = "org.example.Provider",
            name = "Example Provider",
            priority = 10,
            description = "This is a test provider",
            shortDescription = "Test provider"
        )

        assertEquals(dto1, dto2)
        assertEquals(dto1.hashCode(), dto2.hashCode())
    }

    @Test
    fun `should detect inequality when fields differ`() {
        val dto1 = DownloadProviderDto(
            key = "org.example.Provider",
            name = "Example Provider",
            priority = 10,
            description = "This is a test provider",
            shortDescription = "Test provider"
        )

        val dto2 = dto1.copy(priority = 20)

        assertNotEquals(dto1, dto2)
    }

    @Test
    fun `should handle empty strings`() {
        val dto = DownloadProviderDto(
            key = "",
            name = "",
            priority = 0,
            description = "",
            shortDescription = ""
        )

        assertEquals("", dto.key)
        assertEquals("", dto.name)
        assertEquals("", dto.description)
        assertEquals("", dto.shortDescription)
    }

    @Test
    fun `should handle negative priority`() {
        val dto = DownloadProviderDto(
            key = "org.example.Provider",
            name = "Example Provider",
            priority = -10,
            description = "This is a test provider",
            shortDescription = "Test provider"
        )

        assertEquals(-10, dto.priority)
    }

    @Test
    fun `should handle large priority values`() {
        val dto = DownloadProviderDto(
            key = "org.example.Provider",
            name = "Example Provider",
            priority = Int.MAX_VALUE,
            description = "This is a test provider",
            shortDescription = "Test provider"
        )

        assertEquals(Int.MAX_VALUE, dto.priority)
    }

    @Test
    fun `should handle special characters in strings`() {
        val dto = DownloadProviderDto(
            key = $$"org.example.Provider$Inner",
            name = "Example: Provider <with> \"Special\" & Chars",
            priority = 10,
            description = "Description\nwith\nnewlines\nand\ttabs",
            shortDescription = "Short & sweet"
        )

        assertTrue(dto.key.contains("$"))
        assertTrue(dto.name.contains("<"))
        assertTrue(dto.name.contains("\""))
        assertTrue(dto.description.contains("\n"))
    }

    @Test
    fun `should handle very long strings`() {
        val longString = "A".repeat(10000)
        val dto = DownloadProviderDto(
            key = longString,
            name = longString,
            priority = 10,
            description = longString,
            shortDescription = longString
        )

        assertEquals(10000, dto.key.length)
        assertEquals(10000, dto.name.length)
        assertEquals(10000, dto.description.length)
        assertEquals(10000, dto.shortDescription?.length)
    }

    @Test
    fun `should handle Unicode characters`() {
        val dto = DownloadProviderDto(
            key = "org.example.Provider",
            name = "Example Provider 中文 日本語",
            priority = 10,
            description = "Description with émojis 🎮 🎯",
            shortDescription = "Short 描述"
        )

        assertTrue(dto.name.contains("中文"))
        assertTrue(dto.description.contains("🎮"))
        assertTrue(dto.shortDescription!!.contains("描述"))
    }

    @Test
    fun `toString should include all fields`() {
        val dto = DownloadProviderDto(
            key = "org.example.Provider",
            name = "Example Provider",
            priority = 10,
            description = "This is a test provider",
            shortDescription = "Test provider"
        )

        val toString = dto.toString()

        assertTrue(toString.contains("key=org.example.Provider"))
        assertTrue(toString.contains("name=Example Provider"))
        assertTrue(toString.contains("priority=10"))
        assertTrue(toString.contains("description=This is a test provider"))
        assertTrue(toString.contains("shortDescription=Test provider"))
    }

    @Test
    fun `should handle null shortDescription in equality`() {
        val dto1 = DownloadProviderDto(
            key = "org.example.Provider",
            name = "Example Provider",
            priority = 10,
            description = "This is a test provider",
            shortDescription = null
        )

        val dto2 = DownloadProviderDto(
            key = "org.example.Provider",
            name = "Example Provider",
            priority = 10,
            description = "This is a test provider",
            shortDescription = null
        )

        assertEquals(dto1, dto2)
    }

    @Test
    fun `should not equal when one has null shortDescription and other has value`() {
        val dto1 = DownloadProviderDto(
            key = "org.example.Provider",
            name = "Example Provider",
            priority = 10,
            description = "This is a test provider",
            shortDescription = null
        )

        val dto2 = DownloadProviderDto(
            key = "org.example.Provider",
            name = "Example Provider",
            priority = 10,
            description = "This is a test provider",
            shortDescription = "Test"
        )

        assertNotEquals(dto1, dto2)
    }

    @Test
    fun `should be annotated with JsonInclude NON_NULL`() {
        val annotations = DownloadProviderDto::class.annotations
        val jsonIncludeAnnotation = annotations.find {
            it.annotationClass.simpleName == "JsonInclude"
        }

        assertNotNull(jsonIncludeAnnotation)
    }

    @Test
    fun `should handle zero priority`() {
        val dto = DownloadProviderDto(
            key = "org.example.Provider",
            name = "Example Provider",
            priority = 0,
            description = "This is a test provider",
            shortDescription = "Test provider"
        )

        assertEquals(0, dto.priority)
    }

    @Test
    fun `should handle minimum integer priority`() {
        val dto = DownloadProviderDto(
            key = "org.example.Provider",
            name = "Example Provider",
            priority = Int.MIN_VALUE,
            description = "This is a test provider",
            shortDescription = "Test provider"
        )

        assertEquals(Int.MIN_VALUE, dto.priority)
    }

    @Test
    fun `copy should allow changing only shortDescription to null`() {
        val original = DownloadProviderDto(
            key = "org.example.Provider",
            name = "Example Provider",
            priority = 10,
            description = "This is a test provider",
            shortDescription = "Test provider"
        )

        val copied = original.copy(shortDescription = null)

        assertNull(copied.shortDescription)
        assertEquals(original.key, copied.key)
        assertEquals(original.name, copied.name)
        assertEquals(original.priority, copied.priority)
        assertEquals(original.description, copied.description)
    }

    @Test
    fun `should handle whitespace-only strings`() {
        val dto = DownloadProviderDto(
            key = "   ",
            name = "\t\n",
            priority = 10,
            description = "    ",
            shortDescription = "  "
        )

        assertEquals("   ", dto.key)
        assertEquals("\t\n", dto.name)
        assertEquals("    ", dto.description)
        assertEquals("  ", dto.shortDescription)
    }

    @Test
    fun `should handle duplicate DTOs in collections`() {
        val dto1 = DownloadProviderDto(
            key = "org.example.Provider",
            name = "Example Provider",
            priority = 10,
            description = "This is a test provider",
            shortDescription = "Test provider"
        )

        val dto2 = DownloadProviderDto(
            key = "org.example.Provider",
            name = "Example Provider",
            priority = 10,
            description = "This is a test provider",
            shortDescription = "Test provider"
        )

        val set = setOf(dto1, dto2)
        assertEquals(1, set.size) // Should be deduplicated in a set
    }
}

