package org.gameyfin.plugins.store.gogdb

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.gameyfin.pluginapi.gamestore.GameStoreVersion
import java.time.OffsetDateTime

internal const val MAX_EDITION_LENGTH = 100

internal val GOG_ID = Regex("^\\d{1,20}$")
private val STANDARD_EDITION = Regex("(standard|base|default|regular)( (edition|game|version))?", RegexOption.IGNORE_CASE)

private fun JsonElement.child(key: String): JsonElement? = (this as? JsonObject)?.get(key)

private fun JsonElement?.primitiveText(): String? =
    (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

/** Text of a primitive (numbers included), or [default] if the value is missing, null or not a primitive. */
private fun JsonElement.text(key: String, default: String = ""): String = child(key).primitiveText() ?: default

private fun JsonElement.items(key: String): List<JsonElement> = (child(key) as? JsonArray) ?: emptyList()

internal fun List<GameStoreVersion>.sortedByNewest() =
    distinctBy { it.buildId to it.branch }
        .sortedWith(compareByDescending<GameStoreVersion> { it.releasedAt }.thenByDescending { it.buildId })

internal fun parseGogDbProduct(product: JsonElement): List<GameStoreVersion> {
    val builds = product.child("builds") as? JsonArray
    checkNotNull(builds) { "GOGDB product response is missing the builds array" }

    return builds.mapNotNull { build ->
        val generation = (build.child("generation") as? JsonPrimitive)?.intOrNull ?: 0
        if (build.text("os") != "windows" || generation != 2) return@mapNotNull null
        val buildId = build.text("id").trim()
        val label = build.text("version").trim()
        if (buildId.isEmpty() || label.isEmpty()) return@mapNotNull null

        GameStoreVersion(
            buildId = buildId,
            branch = build.text("branch"),
            label = label,
            releasedAt = build.text("date_published")
                .takeIf { it.isNotBlank() }
                ?.let { OffsetDateTime.parse(it).toInstant() }
        )
    }
}

/** IDs of the products a GOG pack includes. Empty for products that are not packs. */
internal fun parseGogDbIncludedGameIds(product: JsonElement): List<String> =
    product.items("includes_games")
        .map { it.primitiveText().orEmpty().trim() }
        .filter { it.isNotEmpty() }

internal fun isGogDbGame(product: JsonElement): Boolean = product.text("type") == "game"

/** Edition names of a GOG product, without the standard edition, which is the unlabeled base product. */
internal fun parseGogDbEditions(product: JsonElement): List<String> =
    product.items("editions")
        .map { it.text("name").trim() }
        .filter { it.isNotEmpty() && it.length <= MAX_EDITION_LENGTH && !STANDARD_EDITION.matches(it) }
        .distinct()
        .take(20)
