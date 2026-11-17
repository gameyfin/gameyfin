package org.gameyfin.app.games.versions

import org.gameyfin.app.games.repositories.GameRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

data class StoreVersionDto(
    val id: Long,
    val buildId: String,
    val branch: String,
    val label: String,
    val releasedAt: Instant?
)

data class GameStoreVersionStatusDto(
    val linkId: Long,
    val store: String,
    /** Display name of the store, or the store ID if its plugin is not active */
    val storeName: String,
    /** Whether the plugin for the store is active. If not, the link is read-only and not refreshed. */
    val storeAvailable: Boolean,
    val storeAppId: String,
    val tracked: Boolean,
    val edition: String?,
    val versionsCheckedAt: Instant?,
    val versions: List<StoreVersionDto>,
    val installedVersionId: Long?,
    val installedVersionSource: LinkSource?,
    val latestVersionId: Long?,
    val updateAvailable: Boolean?
)

data class VersionDetectionResultDto(
    val detected: Boolean,
    val installedVersionId: Long?,
    val installedVersionLabel: String?,
    val message: String
)

@Service
class GameVersionStatusService(
    private val gameRepository: GameRepository,
    private val linkRepository: GameStoreLinkRepository,
    private val versionRepository: StoreVersionRepository,
    private val stateRepository: GameVersionStateRepository,
    private val objectMapper: ObjectMapper,
    private val storeRegistry: GameStoreRegistry
) {

    @Transactional(readOnly = true)
    fun getStatus(gameId: Long): List<GameStoreVersionStatusDto> {
        val links = linkRepository.findAllByGameId(gameId)
        val activeStores = storeRegistry.activeStores().associateBy { it.id }
        return links.map { link ->
            val versions = versionRepository.findAllByLinkId(link.id!!)
                .map { it.toDto() }
                .sortedWith(
                    compareByDescending<StoreVersionDto> { it.releasedAt ?: Instant.MIN }
                        .thenByDescending { it.buildId }
                )
            val state = stateRepository.findByIdOrNull(link.id!!)
            val installedVersionId = state?.installedVersionId
            val installedVersion = versions.firstOrNull { it.id == installedVersionId }
            val newestFirst = compareBy<StoreVersionDto> { it.releasedAt ?: Instant.MIN }.thenBy { it.buildId }
            // A side branch (e.g. "Classic") must not hide a newer release on the main branch
            val latest = versions
                .filter { it.branch == installedVersion?.branch.orEmpty() || it.branch.isEmpty() }
                .maxWithOrNull(newestFirst)
            GameStoreVersionStatusDto(
                linkId = link.id!!,
                store = link.store,
                storeName = activeStores[link.store]?.info?.displayName ?: link.store,
                storeAvailable = link.store in activeStores,
                storeAppId = link.storeAppId,
                tracked = link.tracked,
                edition = link.edition,
                versionsCheckedAt = link.versionsCheckedAt,
                versions = versions,
                installedVersionId = installedVersionId,
                installedVersionSource = state?.source,
                latestVersionId = latest?.id,
                updateAvailable = if (installedVersion == null || latest == null) null
                else installedVersion.id != latest.id
            )
        }
    }

    @Transactional
    fun setInstalledVersion(
        linkId: Long,
        versionId: Long?,
        source: LinkSource = LinkSource.MANUAL
    ): GameStoreVersionStatusDto {
        val link = linkRepository.findByIdOrNull(linkId)
            ?: throw NoSuchElementException("Store link $linkId not found")

        if (versionId == null) {
            stateRepository.deleteById(linkId)
        } else {
            val version = versionRepository.findByIdAndLinkId(versionId, linkId)
                ?: throw IllegalArgumentException("Version $versionId does not belong to store link $linkId")
            val state = stateRepository.findByIdOrNull(linkId)
                ?: GameVersionState(linkId = linkId)
            state.installedVersionId = version.id
            state.source = source
            stateRepository.save(state)
        }
        return getStatus(link.gameId).first { it.linkId == linkId }
    }

    @Transactional
    fun detectInstalledVersion(linkId: Long): VersionDetectionResultDto {
        val link = linkRepository.findByIdOrNull(linkId)
            ?: throw NoSuchElementException("Store link $linkId not found")
        val state = stateRepository.findByIdOrNull(linkId)
        if (state?.source == LinkSource.MANUAL) {
            return VersionDetectionResultDto(
                detected = false,
                installedVersionId = state.installedVersionId,
                installedVersionLabel = state.installedVersionId?.let { id ->
                    versionRepository.findByIdAndLinkId(id, linkId)?.label
                },
                message = "A manually selected version is preserved; clear it before detecting again"
            )
        }

        val detectedVersion = findDetectedVersion(link)

        if (detectedVersion == null) {
            if (state?.source == LinkSource.AUTO) {
                state.installedVersionId = null
                stateRepository.save(state)
            }
            return VersionDetectionResultDto(
                detected = false,
                installedVersionId = null,
                installedVersionLabel = null,
                message = "No library version matching the cached ${link.store} builds was detected"
            )
        }

        val autoState = state ?: GameVersionState(linkId = linkId, source = LinkSource.AUTO)
        autoState.installedVersionId = detectedVersion.id
        autoState.source = LinkSource.AUTO
        stateRepository.save(autoState)
        return VersionDetectionResultDto(
            detected = true,
            installedVersionId = detectedVersion.id,
            installedVersionLabel = detectedVersion.label,
            message = "Detected ${detectedVersion.label}"
        )
    }

    /** Detects the installed version without saving it, so that it can be staged and applied together with other edits. */
    @Transactional(readOnly = true)
    fun previewDetection(linkId: Long): VersionDetectionResultDto {
        val link = linkRepository.findByIdOrNull(linkId)
            ?: throw NoSuchElementException("Store link $linkId not found")
        val detectedVersion = findDetectedVersion(link)
            ?: return VersionDetectionResultDto(
                detected = false,
                installedVersionId = null,
                installedVersionLabel = null,
                message = "No library version matching the cached ${link.store} builds was detected"
            )
        return VersionDetectionResultDto(
            detected = true,
            installedVersionId = detectedVersion.id,
            installedVersionLabel = detectedVersion.label,
            message = "Detected ${detectedVersion.label}"
        )
    }

    private fun findDetectedVersion(link: GameStoreLink): StoreVersion? {
        val values = detectInstalledValues(link.gameId, link.store, link.storeAppId)
        return matchDetectedVersion(values, versionRepository.findAllByLinkId(link.id!!)) {
            Triple(it.buildId, it.branch, it.label)
        }
    }

    /** Reads the build IDs or version names the installed files of the game report for a store product. */
    fun detectInstalledValues(gameId: Long, store: String, storeAppId: String): List<String> {
        val game = gameRepository.findByIdOrNull(gameId)
            ?: throw NoSuchElementException("Game $gameId not found")
        val path = Path.of(game.metadata.path)
        return when (store) {
            "steam" -> detectSteamBuildId(path, storeAppId)?.let(::listOf).orEmpty()
            "gog" -> detectGogValues(path, storeAppId)
            "epic" -> detectEpicValues(path)
            // Local detection only knows the launchers' file formats; other stores need the version picked manually
            else -> emptyList()
        }
    }

    private fun detectSteamBuildId(gamePath: Path, appId: String): String? {
        val appManifest = "appmanifest_$appId.acf"
        val steamApps = ancestorDirectories(gamePath).firstOrNull { directory ->
            directory.fileName?.toString().equals("steamapps", ignoreCase = true)
        } ?: return null
        val manifest = steamApps.resolve(appManifest)
        if (!Files.isRegularFile(manifest)) return null
        val text = Files.readString(manifest)
        val appIdFromFile = ACF_APP_ID.find(text)?.groupValues?.get(1)
        if (appIdFromFile != null && appIdFromFile != appId) return null
        return ACF_BUILD_ID.find(text)?.groupValues?.get(1)
    }

    private fun detectGogValues(gamePath: Path, productId: String): List<String> {
        val expectedName = "goggame-$productId.info"
        val infoFile = ancestorDirectories(gamePath)
            .map { it.resolve(expectedName) }
            .firstOrNull(Files::isRegularFile)
        val infoValues = infoFile?.let { file ->
            val info = objectMapper.readTree(Files.readString(file))
            sequenceOf("buildId", "build_id", "version", "version_name")
                .mapNotNull { key -> info.path(key).takeIf { !it.isMissingNode && !it.isNull }?.asText()?.trim() }
                .filter { it.isNotEmpty() }
                .toList()
        }.orEmpty()
        return infoValues + detectGogInstallerVersions(gamePath)
    }

    /** Offline installers are named like setup_game_1.5.1_(64bit)_(93251).exe, the version is matched against GOG's version names. */
    private fun detectGogInstallerVersions(gamePath: Path): List<String> {
        val names = if (Files.isDirectory(gamePath)) {
            Files.walk(gamePath, 2).use { files ->
                files.filter(Files::isRegularFile).map { it.fileName.toString() }.toList()
            }
        } else {
            listOf(gamePath.fileName.toString())
        }
        return names
            .filter { GOG_INSTALLER_NAME.matches(it) }
            .flatMap { name -> GOG_INSTALLER_VERSION.findAll(name).map { it.groupValues[1] }.toList() }
            .distinct()
    }

    private fun detectEpicValues(gamePath: Path): List<String> {
        val egstore = ancestorDirectories(gamePath)
            .map { it.resolve(".egstore") }
            .firstOrNull(Files::isDirectory)
            ?: return emptyList()
        return EpicManifestReader.readBuildVersions(egstore)
    }

    private fun ancestorDirectories(path: Path): Sequence<Path> {
        var current: Path? = if (Files.isDirectory(path)) path else path.parent
        return sequence {
            repeat(MAX_ANCESTORS) {
                val directory = current ?: return@sequence
                yield(directory)
                current = directory.parent
            }
        }
    }

    private fun StoreVersion.toDto() = StoreVersionDto(id!!, buildId, branch, label, releasedAt)

    companion object {
        private const val MAX_ANCESTORS = 8
        private val ACF_APP_ID = Regex("\"appid\"\\s+\"(\\d+)\"")
        private val GOG_INSTALLER_NAME = Regex("^(setup|patch)_.+\\.(exe|bin)$", RegexOption.IGNORE_CASE)
        private val GOG_INSTALLER_VERSION = Regex("_v?(\\d+(?:\\.\\d+)+[a-z]?)(?=_|\\.(?:exe|bin)$|-\\d+\\.)", RegexOption.IGNORE_CASE)
        private val ACF_BUILD_ID = Regex("\"buildid\"\\s+\"(\\d+)\"")
    }
}

/** Matches detected values to a version: the default branch is preferred, and a label is the last resort. */
internal fun <T> matchDetectedVersion(
    detectedValues: List<String>,
    versions: List<T>,
    fields: (T) -> Triple<String, String, String>
): T? = detectedValues.firstNotNullOfOrNull { detected ->
    versions.firstOrNull { fields(it).let { (build, branch, _) -> build == detected && branch.isEmpty() } }
        ?: versions.firstOrNull { fields(it).first == detected }
        ?: versions.firstOrNull { fields(it).third == detected }
}
