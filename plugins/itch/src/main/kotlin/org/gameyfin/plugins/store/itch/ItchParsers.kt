package org.gameyfin.plugins.store.itch

import org.gameyfin.pluginapi.gamestore.GameStoreProduct
import org.gameyfin.pluginapi.gamestore.GameStoreVersion

/** `<user>.itch.io/<game>`: the part of an itch.io game URL that identifies a game. */
internal const val PRODUCT_ID_PATTERN = "^[a-z0-9][a-z0-9_-]{0,62}\\.itch\\.io/[a-z0-9][a-z0-9_-]{0,100}$"

private val PRODUCT_ID_REGEX = Regex(PRODUCT_ID_PATTERN)
private val GAME_URL_REGEX = Regex("^https://([a-z0-9][a-z0-9_-]*)\\.itch\\.io/([a-z0-9][a-z0-9_-]*)/?$", RegexOption.IGNORE_CASE)

internal fun isValidProductId(id: String) = PRODUCT_ID_REGEX.matches(id)

internal fun normalizeSearchQuery(query: String): String =
    query.replace(Regex("[^\\p{L}\\p{N}']+"), " ").trim()

private fun words(text: String): List<String> =
    normalizeSearchQuery(text).lowercase().split(' ').filter { it.isNotEmpty() }

/**
 * itch.io search is very loose (it also matches descriptions and tags), so keep only games whose title contains at
 * least half of the query words as word prefixes, best match first. Single letters are ignored ("a", "i").
 */
internal fun rankByTitle(results: List<GameStoreProduct>, query: String): List<GameStoreProduct> {
    val queryWords = words(query).filter { it.length > 1 }.ifEmpty { return results }
    return results
        .map { result ->
            val titleWords = words(result.title)
            val matched = queryWords.count { q -> titleWords.any { it.startsWith(q) } }
            Triple(result, matched.toDouble() / queryWords.size, titleWords.joinToString(" ") == words(query).joinToString(" "))
        }
        .filter { it.second >= 0.5 }
        .sortedWith(compareByDescending<Triple<GameStoreProduct, Double, Boolean>> { it.third }.thenByDescending { it.second })
        .map { it.first }
}

private val CELL_SPLIT = Regex("(?=<div[^>]*data-game_id=\")")

// itch.io does not keep the order of HTML attributes stable, so attributes are looked up per tag
private val TITLE_LINK = Regex("<a\\b([^>]*\\bclass=\"[^\"]*\\btitle\\b[^\"]*\"[^>]*)>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
private val AUTHOR = Regex("<div\\b[^>]*\\bclass=\"game_author\"[^>]*><a\\b[^>]*>(.*?)</a>", RegexOption.DOT_MATCHES_ALL)
private val HREF = Regex("\\bhref=\"([^\"]+)\"")
private val THUMBNAIL = Regex("data-lazy_src=\"([^\"]+)\"")

/**
 * Maps the HTML of itch.io's search page (`/search?type=games&q=`) to products.
 * Games on a custom domain are skipped because they cannot be identified by their itch.io URL.
 */
internal fun parseItchSearch(html: String): List<GameStoreProduct> {
    return html.split(CELL_SPLIT).drop(1).mapNotNull { cell ->
        val (attributes, rawTitle) = TITLE_LINK.find(cell)?.destructured ?: return@mapNotNull null
        val href = HREF.find(attributes)?.groupValues?.get(1) ?: return@mapNotNull null
        val match = GAME_URL_REGEX.matchEntire(htmlUnescape(href)) ?: return@mapNotNull null
        val title = htmlUnescape(stripTags(rawTitle)).trim().take(MAX_TITLE_LENGTH)
        if (title.isEmpty()) return@mapNotNull null

        GameStoreProduct(
            id = "${match.groupValues[1]}.itch.io/${match.groupValues[2]}".lowercase(),
            title = title,
            subtitle = AUTHOR.find(cell)?.groupValues?.get(1)?.let { htmlUnescape(stripTags(it)).trim() }
                ?.takeIf { it.isNotEmpty() }?.take(MAX_TITLE_LENGTH),
            imageUrl = THUMBNAIL.find(cell)?.groupValues?.get(1)?.let(::htmlUnescape)?.takeIf { it.startsWith("https://") }
        )
    }.distinctBy { it.id }
}

private val UPLOAD_SPLIT = Regex("<div class=\"upload\">")
private val UPLOAD_NAME = Regex("<strong\\b[^>]*\\bclass=\"name\"[^>]*>([^<]*)</strong>")
private val VERSION_NAME = Regex("<span\\b[^>]*\\bclass=\"version_name\"[^>]*>([^<]*)<")
private const val UPLOADS_END = "class=\"game_comments"
private const val MAX_UPLOAD_CHARS = 2500

/**
 * Maps the downloads listed on a game page to versions. Developers who upload with itch.io's butler tool provide a
 * version name, the others only have a file name. Neither has a date, so versions are listed in page order and
 * Gameyfin cannot know which one is the newest. Downloads that are only meant for other platforms than Windows are
 * skipped, downloads without a platform are kept.
 */
internal fun parseItchUploads(html: String): List<GameStoreVersion> {
    val start = html.indexOf("class=\"uploads\"")
    if (start < 0) return emptyList()
    val section = html.substring(start).let { it.substring(0, it.indexOf(UPLOADS_END).takeIf { end -> end > 0 } ?: it.length) }

    return section.split(UPLOAD_SPLIT).drop(1).mapNotNull { chunk ->
        val upload = chunk.take(MAX_UPLOAD_CHARS)
        val fileName = UPLOAD_NAME.find(upload)?.groupValues?.get(1)?.let { htmlUnescape(it).trim() }
            ?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        if (!isForWindows(upload)) return@mapNotNull null

        val versionName = VERSION_NAME.find(upload)?.groupValues?.get(1)?.let { htmlUnescape(it).trim() }
            ?.removePrefix("Version ")?.trim()?.takeIf { it.isNotEmpty() }
        GameStoreVersion(
            buildId = fileName.take(MAX_ID_LENGTH),
            label = (versionName?.let { "$it ($fileName)" } ?: fileName).take(MAX_TITLE_LENGTH)
        )
    }.distinctBy { it.buildId }
}

private fun isForWindows(upload: String): Boolean {
    val windows = "icon-windows8" in upload
    val other = listOf("icon-tux", "icon-apple", "icon-android").any { it in upload }
    return windows || !other
}

private fun stripTags(text: String) = text.replace(Regex("<[^>]*>"), "")

internal fun htmlUnescape(text: String): String =
    Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos);").replace(text) { match ->
        when (val entity = match.groupValues[1]) {
            "amp" -> "&"
            "lt" -> "<"
            "gt" -> ">"
            "quot" -> "\""
            "apos" -> "'"
            else -> {
                val code = if (entity.startsWith("#x")) entity.drop(2).toIntOrNull(16) else entity.drop(1).toIntOrNull()
                if (code != null && Character.isValidCodePoint(code)) String(Character.toChars(code)) else match.value
            }
        }
    }

private const val MAX_TITLE_LENGTH = 200
private const val MAX_ID_LENGTH = 200
