package org.gameyfin.app.libraries.dto

interface LibraryScanResult {
    /**
     * Number of new games found in the library.
     */
    val new: Int

    /**
     * Number of games removed from the library.
     */
    val removed: Int

    /**
     * Number of ignored games that were not found in the library.
     */
    val unmatched: Int
}

data class QuickScanResult(
    override val new: Int,
    override val removed: Int,
    override val unmatched: Int
) : LibraryScanResult

data class FullScanResult(
    override val new: Int,
    override val removed: Int,
    override val unmatched: Int,
    /**
     * Number of games updated in the library.
     */
    val updated: Int
) : LibraryScanResult