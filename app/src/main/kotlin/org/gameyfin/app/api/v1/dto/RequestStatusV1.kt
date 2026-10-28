package org.gameyfin.app.api.v1.dto

import io.swagger.v3.oas.annotations.media.Schema

data class RequestStatusV1(@field:Schema(allowableValues = ["PENDING", "APPROVED", "REJECTED", "FULFILLED"]) val status: String) :
    StrictInputV1()
