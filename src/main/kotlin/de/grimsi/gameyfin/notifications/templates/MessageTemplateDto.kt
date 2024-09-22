package de.grimsi.gameyfin.notifications.templates

data class MessageTemplateDto(
    val key: String,
    val name: String,
    val description: String,
    val availablePlaceholders: List<String>
)