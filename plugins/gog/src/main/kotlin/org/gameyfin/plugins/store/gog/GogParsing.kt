package org.gameyfin.plugins.store.gog

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.gameyfin.pluginapi.gamestore.GameStoreDlc
import org.gameyfin.pluginapi.gamestore.GameStoreDlcKind
import org.gameyfin.pluginapi.gamestore.GameStoreProduct
import org.gameyfin.pluginapi.gamestore.GameStoreVersion
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

internal const val MAX_TITLE_LENGTH = 255
internal const val MAX_SEARCH_RESULTS = 10
internal val GOG_ID = Regex("^\\d{1,20}$")

internal data class GogBuildPage(
    val totalCount: Int,
    val itemCount: Int,
    val versions: List<GameStoreVersion>
)

private fun JsonElement.child(key: String): JsonElement? = (this as? JsonObject)?.get(key)

private fun JsonElement?.primitiveText(): String? =
    (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

/** Text of a primitive (numbers included), or [default] if the value is missing, null or not a primitive. */
private fun JsonElement.text(key: String, default: String = ""): String = child(key).primitiveText() ?: default

private fun JsonElement.items(key: String): List<JsonElement> = (child(key) as? JsonArray) ?: emptyList()

private val OFFICIAL_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxx")

internal fun List<GameStoreVersion>.sortedByNewest() =
    distinctBy { it.buildId to it.branch }
        .sortedWith(compareByDescending<GameStoreVersion> { it.releasedAt }.thenByDescending { it.buildId })

internal fun parseGogBuildPage(page: JsonElement): GogBuildPage {
    val items = page.child("items") as? JsonArray
    checkNotNull(items) { "GOG version response is missing the items array" }
    val totalCount = (page.child("total_count") as? JsonPrimitive)?.intOrNull ?: -1
    check(totalCount >= 0) { "GOG version response is missing total_count" }

    val versions = items.mapNotNull { item ->
        val buildId = item.text("build_id").trim()
        val label = item.text("version_name").trim()
        if (buildId.isEmpty() || label.isEmpty()) return@mapNotNull null
        GameStoreVersion(
            buildId = buildId,
            branch = item.text("branch"),
            label = label,
            releasedAt = item.text("date_published")
                .takeIf { it.isNotBlank() }
                ?.let { OffsetDateTime.parse(it, OFFICIAL_DATE).toInstant() }
        )
    }
    return GogBuildPage(totalCount, items.size, versions)
}

internal fun parseGogDlc(root: JsonElement): List<GameStoreDlc> =
    root.items("expanded_dlcs").mapNotNull { dlc ->
        val id = dlc.text("id").takeIf { GOG_ID.matches(it) } ?: return@mapNotNull null
        val title = dlc.text("title").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        GameStoreDlc(id, title.take(MAX_TITLE_LENGTH), classifyExtra(title))
    }.distinctBy { it.id }.sortedBy { it.title.lowercase() }

private val BONUS_TITLE = Regex("soundtrack|\\bost\\b|original score|art ?book|wallpaper|making of|comic|\\bbonus\\b", RegexOption.IGNORE_CASE)
private val EDITION_TITLE = Regex("\\bupgrade\\b|\\bedition\\b", RegexOption.IGNORE_CASE)

/** GOG does not tell us what an extra is, so this relies on words in the title. */
internal fun classifyExtra(title: String): GameStoreDlcKind = when {
    BONUS_TITLE.containsMatchIn(title) -> GameStoreDlcKind.BONUS
    EDITION_TITLE.containsMatchIn(title) -> GameStoreDlcKind.EDITION_UPGRADE
    else -> GameStoreDlcKind.DLC
}

internal fun parseGogSearch(root: JsonElement): List<GameStoreProduct> =
    root.items("products").mapNotNull { product ->
        val id = product.text("id").takeIf { GOG_ID.matches(it) } ?: return@mapNotNull null
        val title = product.text("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val year = product.text("releaseDate").take(4).takeIf { it.length == 4 }
        GameStoreProduct(id, title, year, product.text("coverVertical").takeIf { it.isNotBlank() })
    }.take(MAX_SEARCH_RESULTS)

/**
 * Store searches are word based and may return nothing when the query contains punctuation such as a dash.
 * Apostrophes are kept because they are part of words.
 */
internal fun normalizeSearchQuery(query: String): String =
    query.replace(Regex("[^\\p{L}\\p{N}']+"), " ").trim()

private val ROMAN_NUMERALS = mapOf(
    "i" to "1", "ii" to "2", "iii" to "3", "iv" to "4", "v" to "5",
    "vi" to "6", "vii" to "7", "viii" to "8", "ix" to "9", "x" to "10"
)

private fun titleWords(value: String) =
    value.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.isNotEmpty() }
        .map { ROMAN_NUMERALS[it] ?: it }

/**
 * GOG's catalog search is fuzzy and returns loosely related products when a game is not on the store at all.
 * A result is only kept if every word of the query is the start of a word in its title.
 */
internal fun filterByTitleRelevance(query: String, results: List<GameStoreProduct>): List<GameStoreProduct> {
    val queryWords = titleWords(query)
    if (queryWords.isEmpty()) return results
    return results.filter { result ->
        val words = titleWords(result.title)
        queryWords.all { queryWord -> words.any { it.startsWith(queryWord) } }
    }
}
