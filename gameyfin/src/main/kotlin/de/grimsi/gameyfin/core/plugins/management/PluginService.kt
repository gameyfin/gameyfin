package de.grimsi.gameyfin.core.plugins.management

import de.grimsi.gameyfin.core.plugins.config.PluginConfigEntry
import de.grimsi.gameyfin.core.plugins.config.PluginConfigEntryKey
import de.grimsi.gameyfin.core.plugins.config.PluginConfigRepository
import de.grimsi.gameyfin.core.plugins.config.PluginConfigService
import de.grimsi.gameyfin.core.plugins.dto.PluginDto
import de.grimsi.gameyfin.core.plugins.dto.PluginUpdateDto
import de.grimsi.gameyfin.pluginapi.core.Configurable
import de.grimsi.gameyfin.pluginapi.core.GameyfinPlugin
import de.grimsi.gameyfin.pluginapi.core.PluginConfigElement
import de.grimsi.gameyfin.pluginapi.core.PluginConfigValidationResult
import io.github.oshai.kotlinlogging.KotlinLogging
import org.pf4j.ExtensionPoint
import org.pf4j.PluginWrapper
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks

@Service
class PluginService(
    private val pluginManager: GameyfinPluginManager,
    private val pluginConfigService: PluginConfigService,
    private val pluginManagementRepository: PluginManagementRepository,
    private val pluginConfigRepository: PluginConfigRepository
) {
    companion object {
        private val log = KotlinLogging.logger {}
    }

    private val pluginUpdates = Sinks.many().multicast().onBackpressureBuffer<PluginUpdateDto>()
    private val pluginConfigValidationCache = mutableMapOf<String, PluginConfigValidationResult>()

    init {
        pluginManager.addPluginStateListener { event ->
            pluginUpdates.tryEmitNext(PluginUpdateDto(id = event.plugin.pluginId, state = event.pluginState))
        }
    }

    fun subscribe(): Flux<PluginUpdateDto> {
        log.debug { "New subscription for pluginUpdates" }
        return pluginUpdates.asFlux()
            .doOnSubscribe { log.debug { "Subscriber added to pluginUpdates [${pluginUpdates.currentSubscriberCount()}]" } }
            .doFinally {
                log.debug { "Subscriber removed from pluginUpdates with signal type $it [${pluginUpdates.currentSubscriberCount()}]" }
            }
    }

    fun getSupportedPluginTypes(): List<String> {
        return pluginManager.plugins
            .flatMap { pluginManager.getExtensionTypes(it.pluginId) }
    }

    fun getAll(): List<PluginDto> {
        return pluginManager.plugins
            .map { toDto(it) }
    }

    fun getPluginManagementEntry(pluginId: String): PluginManagementEntry {
        return pluginManagementRepository.findByIdOrNull(pluginId)
            ?: throw IllegalArgumentException("Plugin with ID $pluginId not found")
    }

    fun getPluginManagementEntry(clazz: Class<ExtensionPoint>): PluginManagementEntry {
        val pluginWrapper = pluginManager.whichPlugin(clazz)
        return pluginManagementRepository.findByIdOrNull(pluginWrapper.pluginId)
            ?: throw IllegalArgumentException("Plugin with class $clazz not found")
    }

    fun enablePlugin(pluginId: String) {
        pluginManager.enablePlugin(pluginId)
    }

    fun disablePlugin(pluginId: String) {
        pluginManager.disablePlugin(pluginId)
    }

    fun setPluginPriorities(pluginPriorities: Map<String, Int>) {
        pluginPriorities.forEach { (pluginId, priority) ->
            val pluginManagementEntry = getPluginManagementEntry(pluginId)
            pluginManagementEntry.priority = priority
            pluginManagementRepository.save(pluginManagementEntry)
        }
    }

    fun getLogo(pluginId: String): ByteArray? {
        val plugin = pluginManager.getPlugin(pluginId).plugin as GameyfinPlugin
        return plugin.getLogo()
    }

    fun getConfigMetadata(pluginWrapper: PluginWrapper): List<PluginConfigElement> {
        log.debug { "Getting config metadata for plugin ${pluginWrapper.pluginId}" }
        val plugin = pluginWrapper.plugin
        if (plugin !is Configurable) return emptyList()
        return plugin.configMetadata
    }

    fun getConfig(pluginWrapper: PluginWrapper): Map<String, String?> {
        log.debug { "Getting config for plugin ${pluginWrapper.pluginId}" }
        return pluginConfigRepository.findAllById_PluginId(pluginWrapper.pluginId).associate { it.id.key to it.value }
    }

    fun updateConfig(pluginId: String, config: Map<String, String>) {
        log.debug { "Setting config entries for plugin $pluginId" }
        val entries = config.map { PluginConfigEntry(PluginConfigEntryKey(pluginId, it.key), it.value) }

        // Persist new config
        pluginConfigRepository.saveAll(entries)

        // Restart plugin to apply new config
        pluginManager.restart(pluginId)

        // Validate new config
        val result = validatePluginConfig(pluginId, true)

        // Emit update event
        val update = PluginUpdateDto(pluginId, config = config, configValidation = result)
        pluginUpdates.tryEmitNext(update)
    }

    fun validatePluginConfig(pluginId: String, forceRevalidation: Boolean = false): PluginConfigValidationResult {
        if (forceRevalidation || !pluginConfigValidationCache.containsKey(pluginId)) {
            log.debug { "Validating config for plugin $pluginId" }
            val result = pluginManager.validatePluginConfig(pluginId)
            pluginConfigValidationCache[pluginId] = result
            return result
        } else {
            log.debug { "Using cached validation result for plugin $pluginId" }
            return pluginConfigValidationCache[pluginId]!!
        }
    }

    fun validatePluginConfig(pluginId: String, configToValidate: Map<String, String>): PluginConfigValidationResult {
        return pluginManager.validatePluginConfig(pluginId, configToValidate)
    }

    private fun toDto(pluginWrapper: PluginWrapper): PluginDto {
        val pluginManagementEntry = getPluginManagementEntry(pluginWrapper.pluginId)

        val hasLogo = try {
            when (pluginWrapper.plugin is GameyfinPlugin) {
                true -> (pluginWrapper.plugin as GameyfinPlugin).hasLogo()
                false -> false
            }
        } catch (_: NoClassDefFoundError) {
            false
        }

        val descriptor = pluginWrapper.descriptor as GameyfinPluginDescriptor

        return PluginDto(
            id = descriptor.pluginId,
            types = pluginManager.getExtensionTypes(pluginWrapper.pluginId),
            name = descriptor.pluginName,
            description = descriptor.pluginDescription,
            shortDescription = descriptor.pluginShortDescription,
            version = descriptor.version,
            author = descriptor.author,
            license = descriptor.license,
            url = descriptor.pluginUrl,
            hasLogo = hasLogo,
            state = pluginWrapper.pluginState,
            configMetadata = getConfigMetadata(pluginWrapper),
            config = getConfig(pluginWrapper),
            configValidation = validatePluginConfig(descriptor.pluginId),
            priority = pluginManagementEntry.priority,
            trustLevel = pluginManagementEntry.trustLevel
        )
    }
}