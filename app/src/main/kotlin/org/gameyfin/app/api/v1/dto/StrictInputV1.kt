package org.gameyfin.app.api.v1.dto

import com.fasterxml.jackson.annotation.JsonAnySetter

/** Unknown inputs must not be silently accepted when a client targets a newer contract. */
abstract class StrictInputV1 {
    @JsonAnySetter
    @Suppress("UNUSED_PARAMETER")
    fun unknown(name: String, value: Any?) {
        throw IllegalArgumentException("Unknown input field: $name")
    }
}
