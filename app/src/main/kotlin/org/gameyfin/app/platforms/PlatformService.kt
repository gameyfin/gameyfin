package org.gameyfin.app.platforms

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.core.events.*
import org.gameyfin.app.core.plugins.management.GameyfinPluginManager
import org.gameyfin.app.games.repositories.GameRepository
import org.gameyfin.app.libraries.LibraryRepository
import org.gameyfin.app.platforms.dto.PlatformStatsDto
import org.gameyfin.pluginapi.gamemetadata.GameMetadataProvider
import org.gameyfin.pluginapi.gamemetadata.Platform
import org.pf4j.PluginStateEvent
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

@Service
class PlatformService(
    private val gameRepository: GameRepository,
    private val libraryRepository: LibraryRepository,
    private val pluginManager: GameyfinPluginManager
) {
    companion object {
        private val log = KotlinLogging.logger {}

        /* Websockets */
        private val platformUpdates = Sinks.many().multicast().onBackpressureBuffer<PlatformStatsDto>(1024, false)

        fun subscribe(): Flux<List<PlatformStatsDto>> {
            log.debug { "New subscription for platformUpdates (#${platformUpdates.currentSubscriberCount()})" }
            return platformUpdates.asFlux()
                .buffer(100.milliseconds.toJavaDuration())
                .doOnSubscribe { log.debug { "Subscriber added to platformUpdates [${platformUpdates.currentSubscriberCount()}]" } }
                .doFinally {
                    log.debug { "Subscriber removed from platformUpdates with signal type $it [${platformUpdates.currentSubscriberCount()}]" }
                }
        }

        fun emit(stats: PlatformStatsDto) {
            platformUpdates.tryEmitNext(stats)
        }
    }

    private val metadataPlugins: List<GameMetadataProvider>
        get() = pluginManager.getExtensions(GameMetadataProvider::class.java)

    private lateinit var _availablePlatforms: Set<Platform>
    private lateinit var _platformsInUseByGames: Set<Platform>
    private lateinit var _platformsInUseByLibraries: Set<Platform>

    val availablePlatforms: Set<Platform>
        get() = _availablePlatforms

    val platformsInUseByGames: Set<Platform>
        get() = _platformsInUseByGames

    val platformsInUseByLibraries: Set<Platform>
        get() = _platformsInUseByLibraries

    @EventListener(ApplicationReadyEvent::class)
    fun initialize() {
        log.debug { "Initializing platform caches at startup" }
        calculateAvailablePlatforms()
        calculatePlatformsInUseByGames()
        calculatePlatformsInUseByLibraries()
    }

    @Async
    @EventListener(classes = [PluginStateEvent::class])
    fun onPluginStateChange(event: PluginStateEvent) {
        if (!pluginManager.supportsExtensionType(event.plugin.pluginId, GameMetadataProvider::class)) return

        log.debug { "GameMetadataProvider plugin state changed, recalculating available platforms" }
        calculateAvailablePlatforms()
    }

    @Async
    @EventListener(classes = [GameCreatedEvent::class, GameUpdatedEvent::class, GameDeletedEvent::class])
    fun onGameChange() {
        log.debug { "Game data changed, recalculating platforms in use by games" }
        calculatePlatformsInUseByGames()
    }

    @Async
    @EventListener(classes = [LibraryCreatedEvent::class, LibraryUpdatedEvent::class, LibraryDeletedEvent::class])
    fun onLibraryChange() {
        log.debug { "Library data changed, recalculating platforms in use by libraries" }
        calculatePlatformsInUseByLibraries()
    }

    fun getStats(): PlatformStatsDto {
        log.debug { "Fetching platform stats" }
        return PlatformStatsDto(
            available = _availablePlatforms,
            inUseByGames = _platformsInUseByGames,
            inUseByLibraries = _platformsInUseByLibraries
        )
    }

    private fun calculateAvailablePlatforms() {
        log.debug { "Computing available platforms" }

        /*
         * Filter platforms by plugin support
         * Plugins that do not specify any supported platforms are considered to support all platforms
         */
        _availablePlatforms = if (metadataPlugins.any { it.supportedPlatforms.isEmpty() }) {
            log.debug { "At least one metadata plugin supports all platforms" }
            Platform.entries.toSet()
        } else {
            metadataPlugins.flatMap { it.supportedPlatforms }.toSet()
        }

        emit(PlatformStatsDto(available = _availablePlatforms))
    }

    private fun calculatePlatformsInUseByGames() {
        log.debug { "Computing platforms in use by games" }
        _platformsInUseByGames = gameRepository.findAllDistinctPlatforms()

        emit(PlatformStatsDto(inUseByGames = _platformsInUseByGames))
    }

    private fun calculatePlatformsInUseByLibraries() {
        log.debug { "Computing platforms in use by libraries" }
        _platformsInUseByLibraries = libraryRepository.findAllDistinctPlatforms()

        emit(PlatformStatsDto(inUseByLibraries = _platformsInUseByLibraries))
    }
}