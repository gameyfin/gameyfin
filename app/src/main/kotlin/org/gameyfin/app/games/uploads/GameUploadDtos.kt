package org.gameyfin.app.games.uploads

import org.gameyfin.app.games.dto.GameImageSelectionDto
import org.gameyfin.app.games.dto.GameUpdateDto
import org.gameyfin.app.games.entities.Game
import java.time.ZoneOffset
import java.util.*

data class GameUploadFileDto(val path: String, val size: Long)

data class GameUploadDestinationDto(val libraryId: Long, val directory: String, val name: String)

data class GameUploadRequest(
    val libraryId: Long,
    val directory: String,
    val name: String,
    val folder: Boolean,
    val files: List<GameUploadFileDto>
)

data class GameUploadStatus(val id: UUID, val gameId: Long?, val completedFiles: Int)

internal fun Game.uploadDraft(): GameUpdateDto = GameUpdateDto(
    id = 0, title = title, platforms = platforms.toSet(),
    release = release?.atZone(ZoneOffset.UTC)?.toLocalDate(),
    coverUrl = null, headerUrl = null, comment = comment, summary = summary,
    developers = developers.map { it.name }, publishers = publishers.map { it.name },
    genres = genres, themes = themes, features = features, perspectives = perspectives, keywords = keywords,
    metadata = null,
    cover = coverImage?.originalUrl?.let { GameImageSelectionDto(url = it) },
    header = headerImage?.originalUrl?.let { GameImageSelectionDto(url = it) },
    images = images.mapNotNull { it.originalUrl?.let { url -> GameImageSelectionDto(url = url) } },
    videoUrls = videoUrls.map { it.toString() }, mediaOrder = mediaOrder
)
