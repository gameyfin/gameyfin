package org.gameyfin.app.api.v1.dto

data class LibraryPatchV1(
    val name: String? = null,
    val directories: List<DirectoryV1>? = null,
    val platforms: List<String>? = null
)
