package org.gameyfin.app.media

fun Image.toDto(): ImageDto = ImageDto(
    id = this.id!!,
    type = this.type,
    blurhash = this.blurhash
)

fun ImageDto.toEntity(): Image = Image(
    id = this.id,
    type = this.type,
    blurhash = this.blurhash
)