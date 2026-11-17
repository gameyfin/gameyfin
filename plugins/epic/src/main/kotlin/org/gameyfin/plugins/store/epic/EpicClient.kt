package org.gameyfin.plugins.store.epic

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.gameyfin.pluginapi.gamestore.GameStoreDlc
import org.gameyfin.pluginapi.gamestore.GameStoreDlcKind
import org.gameyfin.pluginapi.gamestore.GameStoreInfo
import org.gameyfin.pluginapi.gamestore.GameStoreProduct
import org.gameyfin.pluginapi.gamestore.GameStoreVersion
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

val EPIC_STORE = GameStoreInfo(
    id = "epic",
    displayName = "Epic Games",
    productIdPattern = "^[0-9a-f]{32}$",
    urlPatterns = listOf("egdata\\.app/offers/([0-9a-fA-F]{32})"),
    caseInsensitiveIds = true
)

private const val MAX_SEARCH_RESULTS = 10
private const val MAX_TITLE_LENGTH = 255
private const val OFFERS_URL = "https://api.egdata.app/offers"
private const val SEARCH_URL = "https://api.egdata.app/search/v2/search"

private val ID_REGEX = Regex(EPIC_STORE.productIdPattern)

/**
 * Unofficial Epic source: the community catalog egdata.app, because Epic itself only exposes build data to
 * authenticated launcher clients. The product ID is the Epic offer ID.
 */
class EpicClient(private val http: EpicHttp) {

    fun search(query: String): List<GameStoreProduct> {
        val trimmed = normalizeSearchQuery(query)
        if (trimmed.length < 2) return emptyList()
        val body = buildJsonObject {
            put("title", trimmed.take(100))
            put("offerType", "BASE_GAME")
            put("limit", MAX_SEARCH_RESULTS)
        }.toString()
        return filterByTitleRelevance(trimmed, parseEpicSearch(http.postJson(URI.create(SEARCH_URL), body)))
            .take(MAX_SEARCH_RESULTS)
    }

    fun fetchVersions(productId: String): List<GameStoreVersion> {
        require(ID_REGEX.matches(productId)) { "Invalid Epic offer ID: '$productId'" }
        return parseEpicBuilds(http.getJson(URI.create("$OFFERS_URL/$productId/builds")))
    }

    fun fetchDlc(productId: String): List<GameStoreDlc> {
        require(ID_REGEX.matches(productId)) { "Invalid Epic offer ID: '$productId'" }
        return parseEpicDlc(http.getJson(URI.create("$OFFERS_URL/$productId/related")), productId)
    }
}

/** Mirrors Jackson's `path(name).asText(default)`: missing, null and non-scalar values yield the default. */
private fun JsonElement?.text(default: String = ""): String =
    (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content ?: default

private fun JsonElement?.field(name: String): JsonElement? = (this as? JsonObject)?.get(name)

private fun JsonElement?.items(): List<JsonElement> = when (this) {
    is JsonArray -> this
    is JsonObject -> values.toList()
    else -> emptyList()
}

private fun List<GameStoreVersion>.sortedByNewest() =
    distinctBy { it.buildId to it.branch }
        .sortedWith(compareByDescending<GameStoreVersion> { it.releasedAt }.thenByDescending { it.buildId })

/**
 * Maps egdata.app builds to versions. Only Windows builds on the production ("Live") label are used, unless there
 * are none. Epic has no release dates, so the time egdata first saw a build is used instead.
 */
internal fun parseEpicBuilds(root: JsonElement): List<GameStoreVersion> {
    check(root is JsonArray) { "Epic version response is not an array of builds" }

    val windows = root.filter { it.field("platform").text().equals("Windows", ignoreCase = true) }
    val live = windows.filter { it.field("labelName").text().startsWith("Live", ignoreCase = true) }

    return (live.ifEmpty { windows }).mapNotNull { build ->
        val buildVersion = build.field("buildVersion").text().trim()
        if (buildVersion.isEmpty()) return@mapNotNull null

        val label = build.field("labelName").text().removeSuffix("-Windows").trim()
        val branch = if (label.equals("Live", ignoreCase = true)) "" else label
        val firstSeen = build.field("firstSeenAt").text()
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        Triple(buildVersion, branch, firstSeen)
    }.groupBy { (buildVersion, branch) -> buildVersion to branch }
        .map { (key, builds) ->
            val (buildVersion, branch) = key
            val releasedAt = builds.mapNotNull { it.third }.minOrNull()
            val date = releasedAt?.let {
                DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC).format(it)
            }
            GameStoreVersion(
                buildId = buildVersion,
                branch = branch,
                label = buildString {
                    if (branch.isNotEmpty()) append("$branch: ")
                    append(buildVersion)
                    if (date != null) append(" ($date)")
                },
                releasedAt = releasedAt
            )
        }.sortedByNewest()
}

private val BONUS_TITLE =
    Regex("soundtrack|\\bost\\b|original score|art ?book|wallpaper|making of|comic|\\bbonus\\b", RegexOption.IGNORE_CASE)
private val EDITION_TITLE = Regex("\\bupgrade\\b|\\bedition\\b", RegexOption.IGNORE_CASE)
private val EPIC_INTERNAL_TITLE = Regex("staging|modkit|audience", RegexOption.IGNORE_CASE)

/** Epic's "EDITION" offer type is used as a hint first and words in the title second. */
internal fun classifyExtra(title: String, isEdition: Boolean = false, isBonus: Boolean = false): GameStoreDlcKind = when {
    isBonus || BONUS_TITLE.containsMatchIn(title) -> GameStoreDlcKind.BONUS
    isEdition || EDITION_TITLE.containsMatchIn(title) -> GameStoreDlcKind.EDITION_UPGRADE
    else -> GameStoreDlcKind.DLC
}

/**
 * Epic lists editions next to DLC. They are offered as extras here because they can be an upgrade of the base
 * product. Other offer types (tools, keys, internal staging entries) are skipped.
 */
internal fun parseEpicDlc(root: JsonElement, offerId: String): List<GameStoreDlc> =
    root.items().mapNotNull { offer ->
        val type = offer.field("offerType").text()
        if (type != "DLC" && type != "ADD_ON" && type != "EDITION") return@mapNotNull null
        val id = offer.field("id").text().lowercase().takeIf { ID_REGEX.matches(it) && it != offerId }
            ?: return@mapNotNull null
        val title = offer.field("title").text().trim()
            .takeIf { it.isNotEmpty() && !EPIC_INTERNAL_TITLE.containsMatchIn(it) }
            ?: return@mapNotNull null
        GameStoreDlc(id, title.take(MAX_TITLE_LENGTH), classifyExtra(title, isEdition = type == "EDITION"))
    }.distinctBy { it.id }.sortedBy { it.title.lowercase() }

/**
 * Store searches are word based; punctuation such as a dash in "Wild Hunt — Remastered" is dropped.
 * Apostrophes are kept because they are part of words.
 */
internal fun normalizeSearchQuery(query: String): String =
    query.replace(Regex("[^\\p{L}\\p{N}']+"), " ").trim()

/** Epic's cover images are 1200x1600 (about 600 KB), which is far too heavy for a result list thumbnail. */
internal fun epicThumbnailUrl(url: String): String =
    if (URI.create(url).host == "cdn1.epicgames.com" && '?' !in url) "$url?h=96&resize=1&w=72" else url

internal fun parseEpicSearch(root: JsonElement): List<GameStoreProduct> =
    root.field("offers").items().mapNotNull { offer ->
        val id = offer.field("id").text().lowercase().takeIf { ID_REGEX.matches(it) } ?: return@mapNotNull null
        val title = offer.field("title").text().takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val images = offer.field("keyImages").items()
        val image = listOf("OfferImageTall", "Thumbnail")
            .firstNotNullOfOrNull { type -> images.firstOrNull { it.field("type").text() == type } }
            ?.field("url").text().takeIf { it.isNotBlank() }
            ?.let(::epicThumbnailUrl)
        GameStoreProduct(id, title, offer.field("seller").field("name").text().takeIf { it.isNotBlank() }, image)
    }.take(MAX_SEARCH_RESULTS)

private val ROMAN_NUMERALS = mapOf(
    "i" to "1", "ii" to "2", "iii" to "3", "iv" to "4", "v" to "5",
    "vi" to "6", "vii" to "7", "viii" to "8", "ix" to "9", "x" to "10"
)

private fun titleWords(value: String) =
    value.lowercase().split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.isNotEmpty() }
        .map { ROMAN_NUMERALS[it] ?: it }

/**
 * The egdata search is fuzzy and returns loosely related products when a game is not on the store at all.
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
