package org.gameyfin.app.core.plugins.dto

import com.fasterxml.jackson.annotation.JsonInclude
import java.io.Serializable

@JsonInclude(JsonInclude.Include.ALWAYS)
class PluginConfigMetadataDto(
    val key: String,
    val type: String,
    val label: String,
    val description: String,
    val default: Serializable?,
    val secret: Boolean,
    val required: Boolean,
    val allowedValues: List<String>?
)