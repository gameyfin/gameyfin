package org.gameyfin.app.config.dto

data class CronExpressionVerificationResultDto(
    val valid: Boolean,
    val errorMessage: String? = null
) {
    companion object {
        val valid = CronExpressionVerificationResultDto(true)
        fun invalid(errorMessage: String) = CronExpressionVerificationResultDto(false, errorMessage)
    }
}