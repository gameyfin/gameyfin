package org.gameyfin.app.games.extensions

import org.gameyfin.app.games.entities.*

/** Item sources use indexed keys in the existing metadata map. The marker distinguishes
 * legacy list attribution from a gallery containing items whose source is unknown. */
fun Game.mediaSources(field: String, count: Int): List<GameFieldMetadata?> =
    List(count) { index ->
        if (metadata.fields.containsKey("$field.items")) metadata.fields["$field.$index"]
        else metadata.fields[field]
    }

fun Game.replaceMediaSources(field: String, sources: List<GameFieldMetadata?>, editor: GameFieldMetadata) {
    val copies = sources.map { it?.copyAttribution() }
    metadata.fields.keys.removeAll { it.startsWith("$field.") }
    metadata.fields["$field.items"] = editor.copyAttribution()
    copies.forEachIndexed { index, source ->
        if (source != null) metadata.fields["$field.$index"] = source
    }
}

private fun GameFieldMetadata.copyAttribution(): GameFieldMetadata = GameFieldMetadata(
    source = when (val original = source) {
        is GameFieldPluginSource -> GameFieldPluginSource(original.plugin)
        is GameFieldUserSource -> GameFieldUserSource(original.user)
        is GameFieldApiSource -> GameFieldApiSource(original.credentialId, original.credentialName)
        else -> error("Unsupported media source")
    },
    updatedAt = updatedAt
)
