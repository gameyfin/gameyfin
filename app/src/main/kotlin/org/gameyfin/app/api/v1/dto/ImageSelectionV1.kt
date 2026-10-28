package org.gameyfin.app.api.v1.dto

data class ImageSelectionV1(val id: String? = null, val url: String? = null, val uploadKey: String? = null) :
    StrictInputV1()
