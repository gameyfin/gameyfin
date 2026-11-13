package org.gameyfin.app.notifications

import io.mockk.*
import org.gameyfin.app.core.notifications.*
import org.gameyfin.app.core.tasks.TaskRegistry
import org.gameyfin.pluginapi.notifications.NotificationAudience
import org.gameyfin.pluginapi.notifications.NotificationRecipients
import org.gameyfin.pluginapi.notifications.NotificationRequest
import org.gameyfin.pluginapi.tasks.TaskContext
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpTimeoutException
import java.time.Instant
import kotlin.test.*

class NotificationTasksTest {
    private val mapper = JsonMapper.builder().build()
    private val client = GithubReleaseClient(mapper, "2.7.0-preview")

    private fun release(tag: String, day: Int, prerelease: Boolean = false) = GithubRelease(
        tag, "https://github.com/gameyfin/gameyfin/releases/tag/$tag",
        Instant.parse("2026-11-${day.toString().padStart(2, '0')}T12:00:00Z"), prerelease
    )

    @Test
    fun `release parser accepts arbitrary tags and validates publication dates and URLs`() {
        val body =
            """{"draft":false,"prerelease":true,"tag_name":"v2.0.0.RC1","published_at":"2026-11-01T12:00:00Z","html_url":"https://github.com/gameyfin/gameyfin/releases/tag/v2.0.0.RC1"}"""
        assertEquals(release("v2.0.0.RC1", 1, true), client.parse(body))
        assertEquals("nightly", client.parse(body.replace("v2.0.0.RC1", "nightly"))?.version)
        assertNull(client.parse(body.replace("\"draft\":false", "\"draft\":true")))
        assertFails { client.parse(body.replace("2026-11-01T12:00:00Z", "invalid")) }
        assertFails { client.parse(body.replace("\"published_at\":\"2026-11-01T12:00:00Z\",", "")) }
        assertFails { client.parse(body.replace("https://github.com", "https://evil.example")) }
        assertFails { client.parse(body.replace("v2.0.0.RC1", "")) }
        assertFails { client.parse("{}") }
        assertFails { client.parse("not-json") }
    }

    @Test
    fun `unknown installed version skips network and failures reach task framework`() {
        val releases = mockk<GithubReleaseClient>()
        val notifications = mockk<SystemNotifications>()
        val context = mockk<TaskContext>(relaxed = true)
        every { context.setting<ReleaseChannel>("releaseChannel") } returns null
        val unknown = NotificationTasks(mockk(), mockk(), releases, notifications, "unknown")
        unknown.checkForUpdates(context)
        verify(exactly = 0) { releases.published() }
        val tasks = NotificationTasks(mockk(), mockk(), releases, notifications, "2.7.0")
        every { releases.published() } throws HttpTimeoutException("Timed out")
        assertFailsWith<HttpTimeoutException> { tasks.checkForUpdates(context) }
        every { releases.published() } throws IllegalStateException("GitHub release check failed (HTTP 403)")
        assertFailsWith<IllegalStateException> { tasks.checkForUpdates(context) }
        verify(exactly = 0) { notifications.announceRelease(any(), any()) }
    }

    @Test
    fun `tasks default to daily schedule and retention 30 days`() {
        val definitions = mutableListOf<org.gameyfin.app.core.tasks.TaskDefinition>()
        val registry = mockk<TaskRegistry>(relaxed = true)
        every { registry.register(capture(definitions)) } just Runs
        NotificationTasks(registry, mockk(), mockk(), mockk(), "2.7.0").register()
        assertEquals(setOf("check-for-updates", "notification-archive-cleanup"), definitions.map { it.key }.toSet())
        assertTrue(definitions.all { it.defaultSchedule == "@daily" && it.scheduledByDefault })
        val channel = definitions.single { it.key == "check-for-updates" }.settings.single()
        assertEquals(ReleaseChannel.stable, channel.default)
        assertEquals(listOf(ReleaseChannel.stable, ReleaseChannel.preview), channel.allowedValues)
        assertFailsWith<IllegalArgumentException> {
            org.gameyfin.app.core.tasks.TaskSettingsService.convert(channel, "nightly")
        }
        assertEquals(30, definitions.single { it.key == "notification-archive-cleanup" }.settings.single().default)
    }

    @Test
    fun `channels select by publication date and find installed prerelease regardless of channel`() {
        val releases = mockk<GithubReleaseClient>()
        val notifications = mockk<SystemNotifications>()
        val context = mockk<TaskContext>(relaxed = true)
        val installed = release("v2.0.0.RC1", 5, true)
        val stable = release("v1.0.0", 6)
        val preview = release("nightly", 7, true)
        every { releases.published() } returns listOf(preview, release("v99.0.0", 1), stable, installed)
        every { notifications.announceRelease(any(), installed) } returns true
        val tasks = NotificationTasks(mockk(), mockk(), releases, notifications, "2.0.0.RC1")

        every { context.setting<ReleaseChannel>("releaseChannel") } returns ReleaseChannel.stable
        tasks.checkForUpdates(context)
        verify { notifications.announceRelease(stable, installed) }

        every { context.setting<ReleaseChannel>("releaseChannel") } returns ReleaseChannel.preview
        tasks.checkForUpdates(context)
        verify { notifications.announceRelease(preview, installed) }
    }

    @Test
    fun `missing installed release skips announcements instead of guessing a date`() {
        val releases = mockk<GithubReleaseClient>()
        val notifications = mockk<SystemNotifications>()
        val context = mockk<TaskContext>(relaxed = true)
        every { context.setting<ReleaseChannel>("releaseChannel") } returns ReleaseChannel.preview
        every { releases.published() } returns listOf(release("v2.6.2", 1))
        val result = NotificationTasks(mockk(), mockk(), releases, notifications, "2.7.0-preview")
            .checkForUpdates(context)
        assertEquals("Skipped: no GitHub release found for installed version 2.7.0-preview", result.summary)
        verify(exactly = 0) { notifications.announceRelease(any(), any()) }
    }

    @Test
    fun `no stable releases still reconciles existing notification against installed release`() {
        val releases = mockk<GithubReleaseClient>()
        val notifications = mockk<SystemNotifications>()
        val context = mockk<TaskContext>(relaxed = true)
        val installed = release("preview", 5, true)
        every { context.setting<ReleaseChannel>("releaseChannel") } returns ReleaseChannel.stable
        every { releases.published() } returns listOf(installed)
        every { notifications.announceRelease(installed, installed) } returns false
        NotificationTasks(mockk(), mockk(), releases, notifications, "preview").checkForUpdates(context)
        verify { notifications.announceRelease(installed, installed) }
    }

    @Test
    fun `notification requests reject unsafe links and invalid recipients`() {
        listOf(
            "javascript:alert(1)",
            "data:text/html,hello",
            "//evil.example",
            "/\\evil.example",
            "https://example.com\n"
        ).forEach {
            assertFalse(NotificationStore.safeLink(it), it)
        }
        listOf("/administration", "https://github.com/gameyfin/gameyfin/releases", "http://example.com").forEach {
            assertTrue(NotificationStore.safeLink(it), it)
        }
        assertFailsWith<IllegalArgumentException> {
            NotificationStore.validate(
                NotificationRequest(
                    "Title", "Body",
                    recipients = NotificationRecipients(NotificationAudience.USERS)
                )
            )
        }
    }
}
