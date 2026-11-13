package org.gameyfin.app.core.notifications

import jakarta.annotation.PostConstruct
import org.gameyfin.app.core.tasks.TaskAction
import org.gameyfin.app.core.tasks.TaskDefinition
import org.gameyfin.app.core.tasks.TaskRegistry
import org.gameyfin.pluginapi.core.config.ConfigMetadata
import org.gameyfin.pluginapi.tasks.TaskContext
import org.gameyfin.pluginapi.tasks.TaskResult
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

@Component
class GithubReleaseClient(
    private val mapper: ObjectMapper,
    @param:Value($$"${spring.application.version:unknown}") private val version: String
) {
    private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()

    fun published(): List<GithubRelease> {
        var page = 1
        val releases = mutableListOf<GithubRelease>()
        do {
            val body = fetch("?per_page=100&page=$page") ?: break
            val json = mapper.readTree(body)
            require(json.isArray) { "Malformed GitHub release response" }
            releases += json.mapNotNull { parse(it.toString()) }
            page++
        } while (json.size() == 100)
        return releases
    }

    private fun fetch(suffix: String): String? {
        val request = HttpRequest.newBuilder(URI("https://api.github.com/repos/gameyfin/gameyfin/releases$suffix"))
            .timeout(Duration.ofSeconds(30)).header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28").header("User-Agent", "Gameyfin/$version").GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 404) return null
        check(response.statusCode() == 200) { "GitHub release check failed (HTTP ${response.statusCode()})" }
        return response.body()
    }

    internal fun parse(body: String): GithubRelease? {
        val json = mapper.readTree(body)
        require(json.has("draft") && json.has("prerelease")) { "Malformed GitHub release response" }
        if (json.path("draft").asBoolean()) return null
        val tag = json.path("tag_name").asString()
        require(tag.isNotBlank()) { "Missing GitHub release tag" }
        val publishedAt = Instant.parse(json.path("published_at").asString())
        val url = json.path("html_url").asString()
        require(url.startsWith("https://github.com/gameyfin/gameyfin/releases/tag/") && NotificationStore.safeLink(url)) { "Invalid release URL" }
        return GithubRelease(tag, url, publishedAt, json.path("prerelease").asBoolean())
    }
}

enum class ReleaseChannel { stable, preview }

data class GithubRelease(val version: String, val url: String, val publishedAt: Instant, val prerelease: Boolean)

internal fun releaseTag(version: String): String = version.trim().removePrefix("v")

@Component
class NotificationTasks(
    private val registry: TaskRegistry, private val store: NotificationStore,
    private val releases: GithubReleaseClient, private val notifications: SystemNotifications,
    @param:Value($$"${spring.application.version:unknown}") private val installedVersion: String
) {
    @PostConstruct
    fun register() {
        registry.register(
            TaskDefinition(
                key = "notification-archive-cleanup",
                name = "Notification archive cleanup",
                description = "Deletes archived notifications after the configured retention period",
                defaultSchedule = "@daily",
                settings = listOf(
                    ConfigMetadata(
                        key = "retentionDays",
                        type = Int::class.javaObjectType,
                        label = "Retention (days)",
                        description = "Positive number of days to retain notifications after they are read",
                        default = 30
                    )
                ),
                action = TaskAction.Run { context ->
                    val days = context.setting("retentionDays") ?: 30
                    TaskResult("${store.cleanup(days)} archived notification(s) deleted")
                })
        )
        registry.register(
            TaskDefinition(
                key = "check-for-updates", name = "Check for updates",
                description = "Checks GitHub for a newer Gameyfin release", defaultSchedule = "@daily",
                settings = listOf(
                    ConfigMetadata(
                        key = "releaseChannel",
                        type = ReleaseChannel::class.java,
                        label = "Release channel",
                        description = "Stable releases only, or preview including prereleases",
                        default = ReleaseChannel.stable
                    )
                ),
                action = TaskAction.Run(::checkForUpdates)
            )
        )
    }

    fun checkForUpdates(context: TaskContext): TaskResult {
        if (installedVersion.isBlank() || installedVersion == "unknown") return TaskResult("Skipped: installed version is unknown")
        context.progress("Checking GitHub releases")
        context.throwIfCancelled()
        val channel = context.setting("releaseChannel") ?: ReleaseChannel.stable
        val published = releases.published()
        val installed = published.find { releaseTag(it.version) == releaseTag(installedVersion) }
            ?: return TaskResult("Skipped: no GitHub release found for installed version $installedVersion")
        val release = published.filter { channel == ReleaseChannel.preview || !it.prerelease }
            .maxByOrNull { it.publishedAt } ?: installed
        context.throwIfCancelled()
        return if (notifications.announceRelease(
                release,
                installed
            )
        ) TaskResult("New release: ${release.version}")
        else TaskResult("No new release to announce")
    }
}
