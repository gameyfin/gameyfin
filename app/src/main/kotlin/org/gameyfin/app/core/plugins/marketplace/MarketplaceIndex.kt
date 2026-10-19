package org.gameyfin.app.core.plugins.marketplace

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

/**
 * Structure of a plugin entry in the `plugins.json` index of a marketplace.
 * Compatible with the PF4J update repository format, extended with Gameyfin specific fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MarketplaceIndexPlugin(
    var id: String? = null,
    var name: String? = null,
    var description: String? = null,
    var shortDescription: String? = null,
    var provider: String? = null,
    var license: String? = null,
    var projectUrl: String? = null,
    var logo: String? = null,
    var types: List<String>? = null,
    var tags: List<String>? = null,
    var releases: List<MarketplaceIndexRelease>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MarketplaceIndexRelease(
    var version: String? = null,
    var date: String? = null,
    var requires: String? = null,
    var url: String? = null,
    var sha512sum: String? = null,
    var changelog: String? = null,
)

object MarketplaceIndexParser {

    private val log = KotlinLogging.logger {}

    private val jsonMapper: JsonMapper = JsonMapper.builder()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .build()

    /**
     * Parses a `plugins.json` index. Invalid entries are skipped (and logged).
     * Relative URLs are resolved against [indexUrl].
     *
     * The index is either an object containing a `plugins` array (preferred) or a plain array of plugins
     * (the format used by PF4J update repositories).
     */
    fun parse(json: String, indexUrl: URI, repositoryId: String): Map<String, GameyfinPluginInfo> {
        val root = jsonMapper.readTree(json)
        val pluginsNode = when {
            root.isArray -> root
            root.isObject && root.get("plugins")?.isArray == true -> root.get("plugins")
            else -> throw IllegalArgumentException(
                "Invalid marketplace index: expected an object with a 'plugins' array or an array of plugins"
            )
        }
        // Entries are converted one by one, so a single malformed entry doesn't break the whole marketplace
        val entries: List<MarketplaceIndexPlugin> = pluginsNode.mapIndexedNotNull { index, node ->
            try {
                require(node.isObject) { "expected an object" }
                jsonMapper.treeToValue(node, MarketplaceIndexPlugin::class.java)
            } catch (e: Exception) {
                log.warn { "Skipping invalid plugin entry #$index in marketplace '$repositoryId': ${e.message}" }
                null
            }
        }

        val result = LinkedHashMap<String, GameyfinPluginInfo>()

        for ((id1, name, description, shortDescription, provider, license, projectUrl, logo, types, tags, releases1) in entries) {
            val id = id1?.trim()
            if (id.isNullOrEmpty()) {
                log.warn { "Skipping plugin without ID in marketplace '$repositoryId'" }
                continue
            }
            if (result.containsKey(id)) {
                log.warn { "Skipping duplicate plugin '$id' in marketplace '$repositoryId'" }
                continue
            }

            val releases = releases1.orEmpty().mapNotNull { toRelease(it, id, indexUrl, repositoryId) }
            if (releases.isEmpty()) {
                log.warn { "Skipping plugin '$id' in marketplace '$repositoryId' since it has no valid releases" }
                continue
            }

            result[id] = GameyfinPluginInfo().apply {
                this.id = id
                this.name = name?.takeIf { it.isNotBlank() } ?: id
                this.description = description.orEmpty()
                this.shortDescription = shortDescription
                this.provider = provider
                this.license = license
                this.projectUrl = projectUrl
                this.logoUrl = logo?.let { resolve(indexUrl, it) }?.toString()
                this.types = types.orEmpty()
                this.tags = tags.orEmpty()
                this.releases = releases
                this.repositoryId = repositoryId
            }
        }

        return result
    }

    private fun toRelease(
        release: MarketplaceIndexRelease,
        pluginId: String,
        indexUrl: URI,
        repositoryId: String
    ): GameyfinPluginRelease? {
        val version = release.version?.trim()
        val url = release.url?.trim()
        if (version.isNullOrEmpty() || url.isNullOrEmpty()) {
            log.warn { "Skipping release of plugin '$pluginId' in marketplace '$repositoryId' without version or URL" }
            return null
        }

        val resolvedUrl = resolve(indexUrl, url) ?: run {
            log.warn { "Skipping release $version of plugin '$pluginId' in marketplace '$repositoryId' due to invalid URL '$url'" }
            return null
        }

        return GameyfinPluginRelease().apply {
            this.version = version
            this.date = parseDate(release.date)
            this.requires = release.requires?.trim()?.takeIf { it.isNotEmpty() } ?: "*"
            this.url = resolvedUrl.toString()
            this.sha512sum = release.sha512sum?.trim()?.let { sha ->
                // ".sha512" and relative checksum URLs are resolved relative to the plugin file
                when {
                    sha.equals(".sha512", ignoreCase = true) -> checksumUrlOf(resolvedUrl).toString()

                    sha.matches(Regex("^[0-9a-fA-F]{128}$")) -> sha
                    else -> resolve(indexUrl, sha)?.toString() ?: sha
                }
            }
            this.changelog = release.changelog
        }
    }

    /**
     * Replaces the extension of the file name in [url] with `.sha512` (or appends it if there is none).
     */
    private fun checksumUrlOf(url: URI): URI {
        val path = url.rawPath.orEmpty()
        val fileName = path.substringAfterLast("/")
        val checksumFileName = if (fileName.contains(".")) fileName.substringBeforeLast(".") else fileName
        val checksumPath = path.substring(0, path.length - fileName.length) + "$checksumFileName.sha512"
        val query = url.rawQuery?.let { "?$it" }.orEmpty()
        return url.resolve(URI("$checksumPath$query"))
    }

    private fun resolve(base: URI, reference: String): URI? {
        return try {
            base.resolve(URI(reference.trim()))
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Supports ISO-8601 dates (`yyyy-MM-dd`), date-times and instants. Falls back to the epoch (same as PF4J).
     */
    fun parseDate(value: String?): Date {
        if (value.isNullOrBlank()) return Date(0)
        val trimmed = value.trim()

        return runCatching { Date.from(LocalDate.parse(trimmed).atStartOfDay().toInstant(ZoneOffset.UTC)) }
            .recoverCatching { Date.from(OffsetDateTime.parse(trimmed).toInstant()) }
            .recoverCatching { Date.from(Instant.parse(trimmed)) }
            .getOrElse {
                log.debug { "Could not parse release date '$value'" }
                Date(0)
            }
    }
}
