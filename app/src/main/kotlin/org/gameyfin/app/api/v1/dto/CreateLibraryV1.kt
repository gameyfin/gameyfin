package org.gameyfin.app.api.v1.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateLibraryV1(
    @field:NotBlank @field:Size(max = 255) val name: String,
    @field:Size(min = 1, max = 100) val directories: List<DirectoryV1>,
    val platforms: List<String> = emptyList(), val scanAfterCreation: Boolean = false
) : StrictInputV1()
