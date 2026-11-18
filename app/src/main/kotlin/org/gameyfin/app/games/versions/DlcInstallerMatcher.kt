package org.gameyfin.app.games.versions

import java.text.Normalizer

/**
 * Best-effort detection of DLC from the file and folder names in the folder of a game.
 *
 * Installers are usually named after the product, for example setup_cyberpunk_2077_phantom_liberty_2.0_(12345).exe.
 * A DLC is detected when all words of its title that are not part of the game title appear in a name.
 * If several DLC match the same name, only the most specific ones (most words) are kept, so "Season Pass Bundle"
 * wins over "Season Pass". The result is a suggestion, an admin can always correct it.
 */
internal object DlcInstallerMatcher {

    private val PARENTHESES = Regex("\\([^)]*\\)|\\[[^]]*]")
    private val VERSION = Regex("(?<![a-z0-9])v?\\d+(\\.\\d+)+[a-z]?(?![a-z0-9])", RegexOption.IGNORE_CASE)
    private val EXTENSION = Regex("\\.(exe|bin|zip|rar|7z|sh|pkg|dmg|iso|msi|tar|gz)$", RegexOption.IGNORE_CASE)
    private val DIACRITICS = Regex("\\p{M}+")
    private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")

    private val IGNORED_WORDS = setOf(
        "setup", "patch", "dlc", "the", "and", "of", "a", "an", "for", "bit", "64bit", "32bit", "win", "windows",
        "gog", "en", "english", "install", "installer", "update"
    )

    fun match(gameTitle: String, names: Collection<String>, candidates: Collection<StoreDlcInfo>): List<StoreDlcInfo> {
        val gameWords = words(gameTitle)
        val candidateWords = candidates.associateWith { (words(it.title) - gameWords).filter { w -> w !in IGNORED_WORDS } }
            .filterValues { it.isNotEmpty() }
        if (candidateWords.isEmpty()) return emptyList()

        val matched = linkedSetOf<StoreDlcInfo>()
        names.forEach { name ->
            val nameWords = words(cleanName(name)).toSet()
            val matches = candidateWords.filterValues { nameWords.containsAll(it) }
            val specificity = matches.values.maxOfOrNull { it.size } ?: return@forEach
            matches.filterValues { it.size == specificity }.keys.forEach(matched::add)
        }
        return matched.toList()
    }

    private fun cleanName(name: String) = name
        .replace(EXTENSION, "")
        .replace(PARENTHESES, " ")
        .replace(VERSION, " ")

    private fun words(text: String): List<String> =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(DIACRITICS, "")
            .lowercase()
            .split(NON_ALPHANUMERIC)
            .filter { it.isNotEmpty() }
}
