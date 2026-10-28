package org.gameyfin.app.api.v1

import org.gameyfin.app.collections.entities.Collection
import org.gameyfin.app.games.entities.Game
import org.gameyfin.app.libraries.entities.Library
import org.gameyfin.app.media.Image
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.time.ZoneOffset

fun apiId(id: String): Long =
    id.toLongOrNull()?.takeIf { it > 0 } ?: throw IllegalArgumentException("Invalid resource ID")

fun apiPage(
    page: Int,
    size: Int,
    sort: String,
    allowed: Set<String> = setOf("id", "createdAt", "updatedAt")
): PageRequest {
    require(page >= 0 && size in 1..200 && page.toLong() * size <= Int.MAX_VALUE) { "Invalid page or size (size must be 1..200)" }
    val parts = sort.split(',')
    require(
        parts.size in 1..2 && parts[0] in allowed && (parts.size == 1 || parts[1] in setOf(
            "asc",
            "desc"
        ))
    ) { "Unsupported sort" }
    val order = Sort.by(if (parts.getOrNull(1) == "desc") Sort.Direction.DESC else Sort.Direction.ASC, parts[0])
    return PageRequest.of(page, size, if (parts[0] == "id") order else order.and(Sort.by("id")))
}

fun <T : Any, R> Page<T>.publicPage(map: (T) -> R) = PageV1(content.map(map), number, size, totalElements)
inline fun <reified T : Enum<T>> apiEnum(code: String): T = enumValues<T>().firstOrNull { it.name == code }
    ?: throw IllegalArgumentException("Unknown ${T::class.simpleName} code: $code")

fun Image.publicImage(gameId: Long) = ImageV1(id!!.toString(), "/v1/games/$gameId/images/$id", mimeType, contentLength)
fun Game.publicGame() = GameV1(
    id!!.toString(),
    library.id!!.toString(),
    title,
    createdAt,
    updatedAt,
    platforms.map { it.name },
    release?.atZone(ZoneOffset.UTC)?.toLocalDate(),
    summary,
    comment,
    userRating,
    criticRating,
    publishers.map { it.name },
    developers.map { it.name },
    genres.map { it.name },
    themes.map { it.name },
    features.map { it.name },
    perspectives.map { it.name },
    keywords,
    coverImage?.publicImage(id!!),
    headerImage?.publicImage(id!!),
    images.map { it.publicImage(id!!) },
    videoUrls.map { it.toString() },
    mediaOrder,
    metadata.originalIds.map { ExternalIdV1(it.key.pluginId, it.value) },
    metadata.fileSize
)

fun Library.publicLibrary() = LibraryV1(id!!.toString(), name, platforms.map { it.name }, createdAt, updatedAt)
fun Collection.publicCollection() = CollectionV1(id!!.toString(), name, description, createdAt, updatedAt)
