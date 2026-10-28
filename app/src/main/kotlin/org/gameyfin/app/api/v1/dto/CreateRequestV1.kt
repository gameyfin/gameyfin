package org.gameyfin.app.api.v1.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class CreateRequestV1(
    @field:NotBlank @field:Size(max = 255) val title: String,
    val platform: String,
    val release: LocalDate? = null
) : StrictInputV1()
