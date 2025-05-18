package de.grimsi.gameyfin.core.plugins.management

import de.grimsi.gameyfin.core.plugins.config.PluginConfigService
import de.grimsi.gameyfin.core.plugins.config.PluginConfigValidationResult
import de.grimsi.gameyfin.core.plugins.dto.PluginDto
import de.grimsi.gameyfin.core.plugins.dto.PluginUpdateDto
import de.grimsi.gameyfin.pluginapi.core.GameyfinPlugin
import org.pf4j.ExtensionPoint
import org.pf4j.PluginWrapper
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks

@Service
class PluginManagementService(
    private val pluginManager: GameyfinPluginManager,
    private val pluginConfigService: PluginConfigService,
    private val pluginManagementRepository: PluginManagementRepository,
) {
    private val pluginUpdates = Sinks.many().multicast().onBackpressureBuffer<PluginUpdateDto>()

    init {
        pluginManager.addPluginStateListener {
            pluginUpdates.tryEmitNext(PluginUpdateDto(id = it.plugin.pluginId, state = it.pluginState))
        }
    }

    fun subscribe(): Flux<PluginUpdateDto> {
        return pluginUpdates.asFlux()
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

    fun validatePluginConfig(pluginId: String): PluginConfigValidationResult {
        return pluginManager.validatePluginConfig(pluginId)
    }

    fun updateConfig(pluginId: String, updatedConfig: Map<String, String>) {
        pluginConfigService.updateConfig(pluginId, updatedConfig)
        val update = PluginUpdateDto(pluginId, config = updatedConfig)
        pluginUpdates.tryEmitNext(update)
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
            configMetadata = pluginConfigService.getConfigMetadata(pluginWrapper),
            config = pluginConfigService.getConfig(pluginWrapper),
            priority = pluginManagementEntry.priority,
            trustLevel = pluginManagementEntry.trustLevel
        )
    }
}