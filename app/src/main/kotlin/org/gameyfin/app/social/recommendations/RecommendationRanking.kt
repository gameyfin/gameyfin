package org.gameyfin.app.social.recommendations

import org.gameyfin.app.social.PlayStatus
import kotlin.math.abs
import kotlin.math.sqrt

internal data class GameInterest(val rating: Int?, val status: PlayStatus?, val favourite: Boolean) {
    val weight: Int
        get() = when {
            rating != null -> rating - 3
            favourite -> 2
            status == PlayStatus.WANT_TO_PLAY || status == PlayStatus.PLAYING -> 1
            else -> 0
        }

    val marked: Boolean get() = rating != null || status != null || favourite
}

internal data class GameTags(val genres: Set<String>, val themes: Set<String>, val keywords: Set<String>)

private const val LIMIT = 24

internal fun rankRecommendations(
    gameIds: List<Long>,
    tags: Map<Long, GameTags>,
    own: Map<Long, GameInterest>,
    peers: Collection<Map<Long, GameInterest>>
): DiscoveryResult {
    val catalog = gameIds.toSet()
    val candidates = catalog.filter { own[it]?.marked != true }.toSet()
    val signals = own.filter { (id, interest) -> id in catalog && interest.weight != 0 }
    val contentSeeds = signals.mapNotNull { (id, interest) ->
        tags[id]?.let { ContentSeed(id, interest.weight, it) }
    }.sortedBy { it.id }
    val contentScale = contentSeeds.sumOf { abs(it.weight) }
    val community = communityScores(signals, peers, candidates)

    val ranked = candidates.mapNotNull { id ->
        val content = tags[id]?.let { contentScore(it, contentSeeds, contentScale) }
        val communityScore = community[id]
        val score = when {
            content != null && communityScore != null -> (2 * content.score + communityScore) / 3
            else -> content?.score ?: communityScore ?: 0.0
        }
        if (score <= 0) return@mapNotNull null
        val match = content?.takeIf { it.score > 0 }?.match
        RankedGame(GameRecommendation(id, match?.gameId, match?.tag), score)
    }.sortedWith(compareByDescending<RankedGame> { it.score }.thenBy { it.game.gameId })
        .take(LIMIT).map { it.game }

    if (ranked.isNotEmpty()) return DiscoveryResult(DiscoveryKind.PERSONALIZED, ranked)
    return popularGames(catalog, candidates, peers)
}

private data class RankedGame(val game: GameRecommendation, val score: Double)
private data class ContentSeed(val id: Long, val weight: Int, val tags: GameTags)
private data class ContentMatch(val gameId: Long, val tag: String)
private data class ContentScore(val score: Double, val match: ContentMatch?)

private fun contentScore(candidate: GameTags, seeds: List<ContentSeed>, scale: Int): ContentScore? {
    var total = 0.0
    var strongest = 0.0
    var match: ContentMatch? = null
    var hasOverlap = false
    for (seed in seeds) {
        val (similarity, tag) = similarity(candidate, seed.tags)
        if (tag == null) continue
        hasOverlap = true
        val contribution = similarity * seed.weight
        total += contribution
        if (contribution > strongest) {
            strongest = contribution
            match = ContentMatch(seed.id, tag)
        }
    }
    return if (hasOverlap) ContentScore(total / scale, match) else null
}

private fun similarity(a: GameTags, b: GameTags): Pair<Double, String?> {
    val categories = listOf(a.genres to b.genres, a.themes to b.themes, a.keywords to b.keywords)
    var total = 0.0
    var categoriesCompared = 0
    var strongest = 0.0
    var sharedTag: String? = null
    for ((left, right) in categories) {
        if (left.isEmpty() && right.isEmpty()) continue
        categoriesCompared++
        val shared = left.intersect(right)
        val score = shared.size.toDouble() / (left.size + right.size - shared.size)
        total += score
        if (score > strongest) {
            strongest = score
            sharedTag = shared.minOrNull()
        }
    }
    return if (categoriesCompared == 0) 0.0 to null else (total / categoriesCompared) to sharedTag
}

private data class WeightedSignals(var score: Double = 0.0, var weight: Double = 0.0)

private fun communityScores(
    own: Map<Long, GameInterest>,
    peers: Collection<Map<Long, GameInterest>>,
    candidates: Set<Long>
): Map<Long, Double> {
    if (own.size < 2) return emptyMap()
    val scores = mutableMapOf<Long, WeightedSignals>()
    for (peer in peers) {
        val similarity = tasteSimilarity(own, peer)
        if (similarity <= 0) continue
        for ((id, interest) in peer) {
            if (id !in candidates || (interest.weight == 0 && interest.rating == null)) continue
            val score = scores.getOrPut(id) { WeightedSignals() }
            score.score += similarity * interest.weight
            score.weight += similarity
        }
    }
    return scores.mapValues { (_, signal) -> signal.score / (2 * signal.weight) }
}

private fun tasteSimilarity(own: Map<Long, GameInterest>, peer: Map<Long, GameInterest>): Double {
    var shared = 0
    var product = 0.0
    var ownSquared = 0.0
    var peerSquared = 0.0
    for ((id, interest) in own) {
        val other = peer[id]?.weight ?: continue
        if (other == 0) continue
        shared++
        product += interest.weight * other
        ownSquared += interest.weight * interest.weight
        peerSquared += other * other
    }
    return if (shared >= 2) product / sqrt(ownSquared * peerSquared) else 0.0
}

private data class RatingStats(var total: Long = 0, var count: Int = 0)

private fun popularGames(
    catalog: Set<Long>,
    candidates: Set<Long>,
    peers: Collection<Map<Long, GameInterest>>
): DiscoveryResult {
    val stats = mutableMapOf<Long, RatingStats>()
    for (peer in peers) {
        for ((id, interest) in peer) {
            val rating = interest.rating ?: continue
            if (id !in catalog) continue
            val game = stats.getOrPut(id) { RatingStats() }
            game.total += rating
            game.count++
        }
    }
    if (stats.isEmpty()) return DiscoveryResult(DiscoveryKind.EMPTY, emptyList())
    val average = stats.values.sumOf { it.total }.toDouble() / stats.values.sumOf { it.count }
    val ranked = stats.entries.filter { it.key in candidates }
        .sortedWith(compareByDescending<Map.Entry<Long, RatingStats>> { (_, value) ->
            (value.total + 3 * average) / (value.count + 3)
        }.thenByDescending { it.value.count }.thenBy { it.key })
        .take(LIMIT).map { GameRecommendation(it.key) }
    return DiscoveryResult(if (ranked.isEmpty()) DiscoveryKind.EMPTY else DiscoveryKind.POPULAR, ranked)
}
