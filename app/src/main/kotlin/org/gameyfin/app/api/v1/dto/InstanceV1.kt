package org.gameyfin.app.api.v1.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class InstanceV1(
    val version: String,
    val buildTime: Instant?,
    val gitCommit: String?,
    val features: InstanceFeaturesV1,
    @field:Schema(requiredMode = Schema.RequiredMode.REQUIRED) val apiVersions: List<String> = listOf("v1")
)

data class GameRequestSettingsV1(
    val enabled: Boolean,
    @field:Schema(description = "Instance guest policy. Public API request creation still requires a personal token.")
    val allowGuests: Boolean,
    @field:Schema(description = "Maximum pending requests per user; 0 means unlimited. Administrators are exempt.")
    val maxOpenRequestsPerUser: Int
)

data class InstanceFeaturesV1(
    val sso: FeatureV1,
    val publicAccess: FeatureV1,
    val gameRequests: GameRequestSettingsV1
)

data class FeatureV1(val enabled: Boolean)
