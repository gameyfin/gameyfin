package org.gameyfin.pluginapi.tasks

import java.io.Serializable

/**
 * Selection of games, usable as type of task parameters and settings
 * (e.g. `ConfigMetadata("games", GameSelection::class.java, ...)`).
 * Administrators select the games using a game picker.
 *
 * The IDs can be resolved using [org.gameyfin.pluginapi.data.GameyfinDataService.getGames].
 */
data class GameSelection(val gameIds: List<Long>) : Serializable {

    fun isEmpty(): Boolean = gameIds.isEmpty()

    /** Comma-separated game IDs, as stored by Gameyfin */
    override fun toString(): String = gameIds.joinToString(",")

    companion object {
        /**
         * Parses comma-separated game IDs.
         * @throws IllegalArgumentException if the value contains invalid IDs
         */
        @JvmStatic
        fun parse(value: String): GameSelection = GameSelection(
            value.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { it.toLongOrNull() ?: throw IllegalArgumentException("Invalid game ID: $it") }
                .distinct()
        )
    }
}
