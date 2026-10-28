package org.gameyfin.app.api.v1.dto

data class GameMediaV1(
    val cover: ImageSelectionV1? = null, val header: ImageSelectionV1? = null,
    val images: List<ImageSelectionV1> = emptyList(), val videoUrls: List<String> = emptyList(),
    val mediaOrder: List<String> = emptyList()
) : StrictInputV1()
