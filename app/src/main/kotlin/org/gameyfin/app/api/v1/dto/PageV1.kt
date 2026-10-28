package org.gameyfin.app.api.v1.dto

data class PageV1<T>(val items: List<T>, val page: Int, val size: Int, val totalElements: Long)
