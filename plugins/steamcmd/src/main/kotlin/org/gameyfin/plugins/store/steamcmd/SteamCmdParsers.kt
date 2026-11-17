package org.gameyfin.plugins.store.steamcmd

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.gameyfin.pluginapi.gamestore.GameStoreVersion
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val STEAM_ID = Regex("^\\d{1,20}$")

internal fun isValidSteamId(id: String) = STEAM_ID.matches(id)

private fun JsonElement?.child(key: String): JsonElement? = (this as? JsonObject)?.get(key)

private fun JsonElement?.text(): String? = (this as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

internal fun parseSteamVersions(root: JsonElement, appId: String): List<GameStoreVersion> {
    val branches = root.child("data").child(appId).child("depots").child("branches") as? JsonObject
    check(branches != null) { "Steam version response is missing depot branches for app $appId" }

    return branches.entries.mapNotNull { (branchName, branchInfo) ->
        val buildId = branchInfo.child("buildid").text().orEmpty().trim()
        if (buildId.isEmpty()) return@mapNotNull null

        val normalizedBranch = if (branchName == "public") "" else branchName
        val releasedAt = branchInfo.child("timebuildupdated").text()?.toLongOrNull()
            ?.takeIf { it > 0 }
            ?.let(Instant::ofEpochSecond)
            ?: branchInfo.child("timeupdated").text()?.toLongOrNull()
                ?.takeIf { it > 0 }
                ?.let(Instant::ofEpochSecond)
        val date = releasedAt?.let {
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC).format(it)
        }
        val branchLabel = if (normalizedBranch.isEmpty()) "Public" else normalizedBranch

        GameStoreVersion(
            buildId = buildId,
            branch = normalizedBranch,
            label = buildString {
                append("$branchLabel build $buildId")
                if (date != null) append(" ($date)")
            },
            releasedAt = releasedAt
        )
    }.sortedWith(compareByDescending<GameStoreVersion> { it.releasedAt }.thenByDescending { it.buildId })
}
