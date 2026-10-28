package org.gameyfin.app.api.v1.dto

import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.media.Schema

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Public user profile. Email, account status, SSO status and roles are included only for administrator personal tokens.")
data class UserV1(
    val id: String, val username: String, val hasAvatar: Boolean,
    @field:Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) val avatarId: String?,
    val email: String? = null, val emailConfirmed: Boolean? = null,
    val enabled: Boolean? = null, val managedBySso: Boolean? = null, val roles: List<String>? = null
)
