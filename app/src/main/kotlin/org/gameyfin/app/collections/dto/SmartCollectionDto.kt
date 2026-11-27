package org.gameyfin.app.collections.dto

import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls

enum class SmartMatch { ALL, ANY }

enum class SmartOperator { EQUALS, CONTAINS, STARTS_WITH }

enum class SmartField { TITLE, DEVELOPER, PUBLISHER, GENRE, THEME, KEYWORD, PLATFORM, LIBRARY }

/** A group has a combinator and children. A condition has a field, operator and value. */
data class SmartRule(
    val match: SmartMatch? = null,
    @param:JsonSetter(contentNulls = Nulls.FAIL)
    val rules: List<SmartRule>? = null,
    val field: SmartField? = null,
    val operator: SmartOperator? = null,
    val value: String? = null
)

data class SmartCollectionSettings(val enabled: Boolean, val rule: SmartRule? = null)

data class SmartCollectionPreview(val matching: Int, val added: Int, val removed: Int)

data class SmartRuleOption(val value: String, val label: String)

data class SmartCollectionEditor(
    val settings: SmartCollectionSettings,
    val genres: List<SmartRuleOption>,
    val themes: List<SmartRuleOption>,
    val platforms: List<SmartRuleOption>
)
