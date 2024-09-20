package de.grimsi.gameyfin.logs.dto

import org.springframework.boot.logging.LogLevel

data class LogConfigDto(
    val logFolder: String,
    val maxHistoryDays: Int,
    val logLevel: LogLevel
)
