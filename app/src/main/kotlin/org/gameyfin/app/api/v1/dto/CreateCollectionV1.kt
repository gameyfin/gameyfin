package org.gameyfin.app.api.v1.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateCollectionV1(
    @field:NotBlank @field:Size(max = 255) val name: String,
    val description: String? = null
) : StrictInputV1()
