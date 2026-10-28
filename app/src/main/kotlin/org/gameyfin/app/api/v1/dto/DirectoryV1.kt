package org.gameyfin.app.api.v1.dto

data class DirectoryV1(val internalPath: String, val externalPath: String? = null) : StrictInputV1()
