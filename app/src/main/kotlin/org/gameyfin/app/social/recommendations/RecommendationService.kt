package org.gameyfin.app.social.recommendations

import jakarta.persistence.EntityManager
import jakarta.persistence.Tuple
import java.util.Locale
import org.gameyfin.app.collections.repositories.CollectionRepository
import org.gameyfin.app.config.ConfigProperties.Social.EnableRecommendations
import org.gameyfin.app.config.ConfigService
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.social.MemberGameStateRepository
import org.gameyfin.app.social.MemberIdentity
import org.gameyfin.app.users.preferences.UserPreference
import org.gameyfin.app.users.preferences.UserPreferenceKey
import org.gameyfin.app.users.preferences.UserPreferenceRepository
import org.gameyfin.pluginapi.gamemetadata.Genre
import org.gameyfin.pluginapi.gamemetadata.Theme
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RecommendationService(
    private val members: MemberIdentity,
    private val preferences: UserPreferenceRepository,
    private val config: ConfigService,
    private val states: MemberGameStateRepository,
    private val collections: CollectionRepository,
    private val games: GameRepository,
    private val entities: EntityManager
) {
    @Transactional(readOnly = true)
    fun discover(): DiscoveryResult {
        val memberId = members.requireId()
        if (config.get(EnableRecommendations) != true) {
            throw AccessDeniedException("Game recommendations are disabled")
        }

        val participants = preferences.findAllByIdKey(SHARING_KEY)
            .filter { it.value == "true" && it.id.userId != memberId }
            .map { it.id.userId }
        val activity = readActivity(participants + memberId)
        val own = activity[memberId].orEmpty()
        val peers = activity.filterKeys { it != memberId }.values
        val gameIds = games.findAllIds()
        val tags = if (own.values.any { it.weight != 0 }) readTags() else emptyMap()

        return rankRecommendations(gameIds, tags, own, peers)
    }

    @Transactional(readOnly = true)
    fun sharesActivity(): Boolean =
        preferences.findById(UserPreferenceKey(SHARING_KEY, members.requireId())).orElse(null)?.value == "true"

    @Transactional
    fun setSharesActivity(enabled: Boolean): Boolean {
        val memberId = members.requireId()
        members.lock(memberId)
        val key = UserPreferenceKey(SHARING_KEY, memberId)
        if (enabled) preferences.save(UserPreference(key, "true"))
        else preferences.deleteById(key)
        return enabled
    }

    private fun readActivity(memberIds: List<Long>): Map<Long, Map<Long, GameInterest>> {
        val activity = mutableMapOf<Long, MutableMap<Long, GameInterest>>()
        for (state in states.activity(memberIds)) {
            activity.getOrPut(state.memberId) { mutableMapOf() }[state.gameId] =
                GameInterest(state.rating, state.status, false)
        }
        for ((memberId, gameId) in collections.favouriteGames(memberIds)) {
            val games = activity.getOrPut(memberId) { mutableMapOf() }
            games[gameId] = games[gameId]?.copy(favourite = true) ?: GameInterest(null, null, true)
        }
        return activity
    }

    private fun readTags(): Map<Long, GameTags> {
        val genres = tags("genres") { (it as Genre).takeUnless { genre -> genre == Genre.UNKNOWN }?.displayName }
        val themes = tags("themes") { (it as Theme).takeUnless { theme -> theme == Theme.UNKNOWN }?.displayName }
        val keywords = tags("keywords") { (it as String).trim().lowercase(Locale.ROOT).takeIf(String::isNotEmpty) }
        return (genres.keys + themes.keys + keywords.keys).associateWith { id ->
            GameTags(genres[id].orEmpty(), themes[id].orEmpty(), keywords[id].orEmpty())
        }
    }

    private fun tags(attribute: String, label: (Any) -> String?): Map<Long, Set<String>> =
        entities.createQuery("select g.id, tag from Game g join g.$attribute tag", Tuple::class.java)
            .resultList.mapNotNull { row ->
                val name = row[1]?.let(label) ?: return@mapNotNull null
                (row[0] as Long) to name
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, labels) -> labels.toSet() }

    private companion object {
        const val SHARING_KEY = "share-game-activity-for-recommendations"
    }
}
