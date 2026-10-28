package org.gameyfin.app.core.plugins.marketplace

import java.net.URI
import java.net.URISyntaxException

/**
 * Resolves user input (e.g. a GitHub repository URL) to the URL of a marketplace's `plugins.json` index.
 *
 * Supported formats:
 * - `https://github.com/<owner>/<repo>` → `plugins.json` in the root of the default branch
 * - `https://github.com/<owner>/<repo>/tree/<branch>[/<path>]` → `plugins.json` in the given branch/directory
 * - `https://github.com/<owner>/<repo>/blob/<branch>/<path>.json` → the given file
 * - Any URL ending with `.json` → used as-is
 * - Any other URL → `plugins.json` relative to the given URL (treated as directory)
 *
 * Inputs without a scheme are treated as HTTPS URLs.
 */
object MarketplaceUrlResolver {

    const val INDEX_FILE_NAME = "plugins.json"
    private const val GITHUB_RAW_HOST = "raw.githubusercontent.com"
    private val GITHUB_HOSTS = setOf("github.com", "www.github.com")

    /**
     * @param allowInsecure if true, `http` and `file` URLs are accepted as well (intended for development only)
     * @throws IllegalArgumentException if the input is not a valid marketplace URL
     */
    fun resolveIndexUrl(input: String, allowInsecure: Boolean = false): URI {
        val trimmed = input.trim()
        require(trimmed.isNotEmpty()) { "URL must not be empty" }

        // "host:port/path" must not be mistaken for a URL with scheme "host", so a scheme is only detected if it is
        // followed by a slash (e.g. "https://..." or "file:/...")
        val withScheme = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:/").containsMatchIn(trimmed)) trimmed else "https://$trimmed"

        val uri = try {
            URI(withScheme).normalize()
        } catch (e: URISyntaxException) {
            throw IllegalArgumentException("Invalid URL: ${e.message}")
        }

        val scheme = uri.scheme?.lowercase()
        val allowedSchemes = if (allowInsecure) setOf("https", "http", "file") else setOf("https")
        require(scheme in allowedSchemes) {
            "Unsupported URL scheme '${uri.scheme}', allowed: ${allowedSchemes.joinToString()}"
        }

        if (scheme != "file") {
            require(!uri.host.isNullOrBlank()) { "URL must contain a host" }
            require(uri.userInfo == null) { "URLs with credentials are not supported" }
        }

        if (uri.host?.lowercase() in GITHUB_HOSTS) {
            return resolveGitHubUrl(uri)
        }

        val path = uri.path.orEmpty()
        if (path.endsWith(".json", ignoreCase = true)) return uri.withoutFragment()

        // Built from the raw (still encoded) components, so escaped characters in the path/query are preserved
        val rawPath = uri.rawPath.orEmpty()
        val directory = if (rawPath.endsWith("/")) rawPath else "$rawPath/"
        return URI(buildString {
            append(uri.scheme).append(':')
            uri.rawAuthority?.let { append("//").append(it) }
            append(directory).append(INDEX_FILE_NAME)
            uri.rawQuery?.let { append('?').append(it) }
        })
    }

    private fun resolveGitHubUrl(uri: URI): URI {
        val segments = uri.path.orEmpty().split("/").filter { it.isNotBlank() }
        require(segments.size >= 2) { "GitHub URLs must point to a repository (https://github.com/<owner>/<repo>)" }

        val owner = segments[0]
        val repo = segments[1].removeSuffix(".git")

        val (ref, filePath) = when {
            segments.size == 2 -> "HEAD" to INDEX_FILE_NAME
            segments.size >= 4 && segments[2] == "tree" -> {
                val directory = segments.drop(4).joinToString("/")
                segments[3] to if (directory.isEmpty()) INDEX_FILE_NAME else "$directory/$INDEX_FILE_NAME"
            }

            segments.size >= 5 && segments[2] == "blob" -> segments[3] to segments.drop(4).joinToString("/")
            else -> throw IllegalArgumentException("Unsupported GitHub URL format: $uri")
        }

        return URI("https", GITHUB_RAW_HOST, "/$owner/$repo/$ref/$filePath", null)
    }

    private fun URI.withoutFragment(): URI {
        return if (rawFragment == null) this else URI(toString().substringBefore('#'))
    }
}
