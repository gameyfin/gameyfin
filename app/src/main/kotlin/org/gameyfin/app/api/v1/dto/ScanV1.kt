package org.gameyfin.app.api.v1.dto

import io.swagger.v3.oas.annotations.media.Schema

data class ScanV1(@field:Schema(allowableValues = ["QUICK", "FULL"]) val type: String = "QUICK") : StrictInputV1()
