package org.gameyfin.app.core.tasks

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.gameyfin.app.core.tasks.persistence.TaskSettingEntry
import org.gameyfin.app.core.tasks.persistence.TaskSettingKey
import org.gameyfin.app.core.tasks.persistence.TaskSettingRepository
import org.gameyfin.pluginapi.core.config.ConfigMetadata
import org.gameyfin.pluginapi.tasks.GameSelection
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaskSettingsServiceTest {

    enum class Mode { FAST, THOROUGH }

    private lateinit var repository: TaskSettingRepository
    private lateinit var service: TaskSettingsService

    private val limit = ConfigMetadata("limit", Int::class.javaObjectType, "Limit", "", default = 10)
    private val enabled = ConfigMetadata("enabled", Boolean::class.javaObjectType, "Enabled", "", default = false)
    private val mode = ConfigMetadata("mode", Mode::class.java, "Mode", "", default = Mode.FAST)
    private val target = ConfigMetadata("target", String::class.java, "Target", "")
    private val comment = ConfigMetadata("comment", String::class.java, "Comment", "", isRequired = false)
    private val games = ConfigMetadata("games", GameSelection::class.java, "Games", "", isRequired = false)

    private val definition = TaskDefinition(
        key = "test",
        name = "Test",
        settings = listOf(limit, enabled, mode, target, comment),
        action = TaskAction.Run { null }
    )

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        every { repository.findById(any()) } returns Optional.empty()
        every { repository.findAllByIdTaskKey(any()) } returns emptyList()
        every { repository.save(any<TaskSettingEntry>()) } answers { firstArg() }
        service = TaskSettingsService(repository)
    }

    private fun stored(key: String, value: String?) {
        every { repository.findById(TaskSettingKey("test", key)) } returns
                Optional.of(TaskSettingEntry(TaskSettingKey("test", key), value))
    }

    @Test
    fun `convert should parse values according to their type`() {
        assertEquals(5, TaskSettingsService.convert(limit, " 5 "))
        assertEquals(true, TaskSettingsService.convert(enabled, "true"))
        assertEquals(Mode.THOROUGH, TaskSettingsService.convert(mode, "THOROUGH"))
        assertEquals(" text ", TaskSettingsService.convert(target, " text "))
    }

    @Test
    fun `convert should reject invalid values`() {
        assertThrows<IllegalArgumentException> { TaskSettingsService.convert(limit, "abc") }
        assertThrows<IllegalArgumentException> { TaskSettingsService.convert(enabled, "yes") }
        assertThrows<IllegalArgumentException> { TaskSettingsService.convert(mode, "SLOW") }
    }

    @Test
    fun `get should return the stored value`() {
        stored("limit", "25")
        stored("mode", "THOROUGH")

        assertEquals(25, service.get<Int>(definition, "limit"))
        assertEquals(Mode.THOROUGH, service.get<Mode>(definition, "mode"))
    }

    @Test
    fun `get should fall back to the default value`() {
        stored("enabled", "invalid")

        assertEquals(10, service.get<Int>(definition, "limit"))
        assertEquals(false, service.get<Boolean>(definition, "enabled"))
        assertNull(service.get<String>(definition, "target"))
    }

    @Test
    fun `get should reject unknown settings`() {
        assertThrows<IllegalArgumentException> { service.get<String>(definition, "unknown") }
    }

    @Test
    fun `getRaw should merge stored values and defaults`() {
        every { repository.findAllByIdTaskKey("test") } returns listOf(
            TaskSettingEntry(TaskSettingKey("test", "limit"), "7"),
            TaskSettingEntry(TaskSettingKey("test", "obsolete"), "x")
        )

        assertEquals(
            mapOf("limit" to "7", "enabled" to "false", "mode" to "FAST", "target" to null, "comment" to null),
            service.getRaw(definition)
        )
    }

    @Test
    fun `validate should report invalid, missing and unknown settings`() {
        val errors = service.validate(
            definition,
            mapOf("limit" to "abc", "mode" to "FAST", "unknown" to "1")
        )

        assertEquals(setOf("limit", "target", "unknown"), errors.keys)
    }

    @Test
    fun `validate should accept valid settings`() {
        assertTrue(service.validate(definition, mapOf("target" to "x", "limit" to "", "comment" to null)).isEmpty())
    }

    @Test
    fun `update should store values and delete empty ones`() {
        service.update(definition, mapOf("target" to "x", "limit" to ""))

        verify { repository.save(match { it.id == TaskSettingKey("test", "target") && it.value == "x" }) }
        verify { repository.deleteById(TaskSettingKey("test", "limit")) }
    }

    @Test
    fun `update should reject invalid settings`() {
        assertThrows<IllegalArgumentException> { service.update(definition, mapOf("target" to "x", "limit" to "y")) }
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `toDto should use primitive type names`() {
        assertEquals("int", TaskSettingsService.toDto(limit).type)
        assertEquals("boolean", TaskSettingsService.toDto(enabled).type)
        assertEquals("String", TaskSettingsService.toDto(target).type)
        assertEquals(listOf("FAST", "THOROUGH"), TaskSettingsService.toDto(mode).allowedValues)
    }

    @Test
    fun `convert should parse game selections`() {
        assertEquals(GameSelection(listOf(3L, 1L)), TaskSettingsService.convert(games, "3, 1,3"))
        assertThrows<IllegalArgumentException> { TaskSettingsService.convert(games, "1,abc") }
    }

    @Test
    fun `convertValues should convert values and use defaults for missing ones`() {
        val values = TaskSettingsService.convertValues(
            listOf(limit, enabled, games),
            mapOf("enabled" to "true", "games" to "7")
        )

        assertEquals(mapOf("limit" to 10, "enabled" to true, "games" to GameSelection(listOf(7L))), values)
    }

    @Test
    fun `convertValues should reject invalid, missing and unknown values`() {
        val error = assertThrows<IllegalArgumentException> {
            TaskSettingsService.convertValues(listOf(limit, target), mapOf("limit" to "abc", "other" to "1"))
        }

        assertTrue(error.message!!.contains("limit"))
        assertTrue(error.message!!.contains("target"))
        assertTrue(error.message!!.contains("other"))
    }

    @Test
    fun `toDto should represent game selections as strings`() {
        val withDefault = ConfigMetadata("games", GameSelection::class.java, "Games", "", default = GameSelection(listOf(1L, 2L)))

        assertEquals("GameSelection", TaskSettingsService.toDto(withDefault).type)
        assertEquals("1,2", TaskSettingsService.toDto(withDefault).default)
        assertNull(TaskSettingsService.toDto(games).default)
    }
}
