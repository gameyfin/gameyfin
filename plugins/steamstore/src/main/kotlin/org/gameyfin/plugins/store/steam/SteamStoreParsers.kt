package org.gameyfin.plugins.store.steam

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.gameyfin.pluginapi.gamestore.GameStoreDlcKind
import org.gameyfin.pluginapi.gamestore.GameStoreProduct

internal const val MAX_TITLE_LENGTH = 255
internal const val MAX_SEARCH_RESULTS = 10
private val STEAM_ID = Regex("^\\d{1,20}$")

internal fun isValidSteamId(id: String) = STEAM_ID.matches(id)

private fun JsonElement?.child(key: String): JsonElement? = (this as? JsonObject)?.get(key)

private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

private fun JsonElement?.items(): List<JsonElement> = (this as? JsonArray)?.toList() ?: emptyList()

private fun JsonElement?.isSuccess(): Boolean = child("success").text() == "true"

internal fun parseSteamDlcIds(root: JsonElement, appId: String): List<String> {
    val app = root.child(appId)
    if (!app.isSuccess()) return emptyList()
    return app.child("data").child("dlc").items()
        .mapNotNull { it.text()?.takeIf(::isValidSteamId) }
        .distinct()
}

internal fun parseSteamAppName(root: JsonElement, appId: String): String? {
    val app = root.child(appId)
    if (!app.isSuccess()) return null
    return app.child("data").child("name").text().orEmpty().trim().takeIf { it.isNotEmpty() }?.take(MAX_TITLE_LENGTH)
}

internal fun parseSteamAppType(root: JsonElement, appId: String): String? {
    val app = root.child(appId)
    if (!app.isSuccess()) return null
    return app.child("data").child("type").text().orEmpty().takeIf { it.isNotEmpty() }
}

internal fun parseSteamSearch(root: JsonElement): List<GameStoreProduct> =
    root.child("items").items().mapNotNull { item ->
        val id = item.child("id").text().orEmpty().takeIf(::isValidSteamId) ?: return@mapNotNull null
        val title = item.child("name").text().orEmpty().takeIf { it.isNotBlank() } ?: return@mapNotNull null
        GameStoreProduct(id, title, null, item.child("tiny_image").text().orEmpty().takeIf { it.isNotBlank() })
    }.take(MAX_SEARCH_RESULTS)

/**
 * Steam's search returns nothing when the query contains punctuation such as a dash in "Wild Hunt — Remastered".
 * Apostrophes are kept because they are part of words.
 */
internal fun normalizeSearchQuery(query: String): String =
    query.replace(Regex("[^\\p{L}\\p{N}']+"), " ").trim()

/** Drops results known to be DLC, soundtracks and the like. Results whose type is unknown are kept. */
internal fun keepBaseGames(
    results: List<GameStoreProduct>,
    typeOf: (String) -> String?
): List<GameStoreProduct> {
    val types = results.parallelStream().map { it.id to typeOf(it.id) }.toList().toMap()
    return results.filter { types[it.id].let { type -> type == null || type == "game" } }
}

private val BONUS_TITLE = Regex("soundtrack|\\bost\\b|original score|art ?book|wallpaper|making of|comic|\\bbonus\\b", RegexOption.IGNORE_CASE)
private val EDITION_TITLE = Regex("\\bupgrade\\b|\\bedition\\b", RegexOption.IGNORE_CASE)

/** Steam does not say what an extra is, so this uses its "music" type and falls back to words in the title. */
internal fun classifyExtra(title: String, isEdition: Boolean = false, isBonus: Boolean = false): GameStoreDlcKind = when {
    isBonus || BONUS_TITLE.containsMatchIn(title) -> GameStoreDlcKind.BONUS
    isEdition || EDITION_TITLE.containsMatchIn(title) -> GameStoreDlcKind.EDITION_UPGRADE
    else -> GameStoreDlcKind.DLC
}
