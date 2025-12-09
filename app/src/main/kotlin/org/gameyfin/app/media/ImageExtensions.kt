package org.gameyfin.app.media

fun Image.toDto(): ImageDto = ImageDto(
    id = this.id.toString(),
    type = this.type,
    blurhash = this.blurhash
)

fun ImageDto.toEntity(): Image = Image(
    id = this.id.toLongOrNull(),
    type = this.type,
    blurhash = this.blurhash
)