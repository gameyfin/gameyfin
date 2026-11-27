package org.gameyfin.app.collections

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Predicate
import org.gameyfin.app.collections.dto.SmartField
import org.gameyfin.app.collections.dto.SmartMatch
import org.gameyfin.app.collections.dto.SmartOperator
import org.gameyfin.app.collections.dto.SmartRule
import org.gameyfin.app.games.dto.GameField
import org.gameyfin.app.games.entities.Company
import org.gameyfin.app.games.entities.Game
import org.gameyfin.pluginapi.gamemetadata.Genre
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.gameyfin.pluginapi.gamemetadata.Theme
import org.springframework.data.jpa.domain.Specification
import java.util.*

internal object SmartCollectionRules {
    fun validate(root: SmartRule) {
        var count = 0
        fun validate(rule: SmartRule, depth: Int) {
            require(depth <= 5 && ++count <= 50) { "Use at most 50 rules and groups, up to five levels deep" }
            if (rule.match != null) {
                require(rule.field == null && rule.operator == null && rule.value == null) { "Invalid rule group" }
                val children = rule.rules.orEmpty()
                require(children.isNotEmpty()) { "Add at least one rule to each group" }
                children.forEach { validate(it, depth + 1) }
                return
            }

            val field = requireNotNull(rule.field) { "Choose a rule field" }
            val operator = requireNotNull(rule.operator) { "Choose a rule operator" }
            val input = rule.value.orEmpty()
            require(rule.rules == null && input.length <= 255 && input.isNotBlank()) {
                "Enter a rule value of 1–255 characters"
            }
            val value = input.trim()
            when (field) {
                SmartField.TITLE, SmartField.DEVELOPER, SmartField.PUBLISHER, SmartField.KEYWORD -> Unit
                else -> {
                    require(operator == SmartOperator.EQUALS) { "This field supports only equals" }
                    when (field) {
                        SmartField.GENRE -> enumValueOf<Genre>(value)
                        SmartField.THEME -> enumValueOf<Theme>(value)
                        SmartField.PLATFORM -> enumValueOf<Platform>(value)
                        SmartField.LIBRARY -> require((value.toLongOrNull() ?: 0) > 0) { "Choose a library" }
                    }
                }
            }
        }
        require(root.match != null) { "Rules must start with a group" }
        validate(root, 1)
    }

    private val fieldMappings = mapOf(
        SmartField.TITLE to GameField.TITLE,
        SmartField.DEVELOPER to GameField.DEVELOPERS,
        SmartField.PUBLISHER to GameField.PUBLISHERS,
        SmartField.GENRE to GameField.GENRES,
        SmartField.THEME to GameField.THEMES,
        SmartField.KEYWORD to GameField.KEYWORDS,
        SmartField.PLATFORM to GameField.PLATFORMS,
        SmartField.LIBRARY to GameField.LIBRARY_ID
    )

    val supportedGameFields = fieldMappings.values.toSet()

    fun gameFields(rule: SmartRule): Set<GameField> = if (rule.match != null) {
        rule.rules.orEmpty().flatMap(::gameFields).toSet()
    } else {
        setOf(fieldMappings.getValue(requireNotNull(rule.field)))
    }

    fun specification(root: SmartRule): Specification<Game> = Specification { game, query, cb ->
        fun predicate(rule: SmartRule): Predicate {
            val match = rule.match
            if (match != null) {
                val predicates = rule.rules.orEmpty().map(::predicate).toTypedArray()
                return if (match == SmartMatch.ALL) cb.and(*predicates) else cb.or(*predicates)
            }

            val value = requireNotNull(rule.value).trim()
            val operator = requireNotNull(rule.operator)
            return when (requireNotNull(rule.field)) {
                SmartField.TITLE -> text(cb, game.get("title"), operator, value)
                SmartField.LIBRARY -> cb.equal(game.get<Any>("library").get<Long>("id"), value.toLong())
                SmartField.GENRE -> cb.isMember(enumValueOf<Genre>(value), game.get("genres"))
                SmartField.THEME -> cb.isMember(enumValueOf<Theme>(value), game.get("themes"))
                SmartField.PLATFORM -> cb.isMember(enumValueOf<Platform>(value), game.get("platforms"))
                SmartField.DEVELOPER, SmartField.PUBLISHER, SmartField.KEYWORD -> {
                    // A separate EXISTS per condition lets different values satisfy different rules.
                    val subquery = requireNotNull(query).subquery(Int::class.javaObjectType)
                    val owner = subquery.correlate(game)
                    val attribute = when (rule.field) {
                        SmartField.DEVELOPER -> owner.join<Game, Company>("developers").get<String>("name")
                        SmartField.PUBLISHER -> owner.join<Game, Company>("publishers").get<String>("name")
                        else -> owner.join<Game, String>("keywords")
                    }
                    cb.exists(subquery.select(cb.literal(1)).where(text(cb, attribute, operator, value)))
                }
            }
        }

        predicate(root)
    }

    private fun text(cb: CriteriaBuilder, path: Expression<String>, operator: SmartOperator, value: String): Predicate {
        val lowerPath = cb.lower(path)
        val lowerValue = value.lowercase(Locale.ROOT)
        if (operator == SmartOperator.EQUALS) return cb.equal(lowerPath, lowerValue)

        val literal = lowerValue.replace("!", "!!").replace("%", "!%").replace("_", "!_")
        val pattern = if (operator == SmartOperator.CONTAINS) "%$literal%" else "$literal%"
        return cb.like(lowerPath, pattern, '!')
    }
}
