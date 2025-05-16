package de.grimsi.gameyfin.core.plugins.management

import de.grimsi.gameyfin.core.plugins.config.PluginConfigValidationResult
import de.grimsi.gameyfin.pluginapi.core.GameyfinPlugin
import org.pf4j.ExtensionPoint
import org.pf4j.PluginWrapper
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class PluginManagementService(
    private val pluginManager: GameyfinPluginManager,
    private val pluginManagementRepository: PluginManagementRepository,
) {

    fun getSupportedPluginTypes(): Set<String> {
        return pluginManager.plugins
            .flatMap { pluginManager.getExtensionTypes(it.pluginId) }
            .toSet()
    }

    fun getPluginDtos(type: String?): Set<PluginDto> {
        return pluginManager.plugins
            .filter { type == null || type in pluginManager.getExtensionTypes(it.pluginId) }
            .map { toDto(it) }
            .toSet()
    }

    fun getPluginDtosMappedToTypes(): Map<String, List<PluginDto>> {
        return pluginManager.plugins
            .flatMap { plugin ->
                val types = pluginManager.getExtensionTypes(plugin.pluginId)
                types.map { it to toDto(plugin) }
            }
            .groupBy({ it.first }, { it.second })
    }

    fun getPluginDto(pluginId: String): PluginDto {
        val plugin = pluginManager.getPlugin(pluginId)
        return toDto(plugin)
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

    fun startPlugin(pluginId: String) {
        pluginManager.startPlugin(pluginId)
    }

    fun stopPlugin(pluginId: String) {
        pluginManager.stopPlugin(pluginId)
    }

    fun restartPlugin(pluginId: String) {
        pluginManager.restart(pluginId)
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

    fun setPluginPriority(pluginId: String, priority: Int) {
        val pluginManagementEntry = getPluginManagementEntry(pluginId)
        pluginManagementEntry.priority = priority
        pluginManagementRepository.save(pluginManagementEntry)
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
            name = descriptor.pluginName,
            description = descriptor.pluginDescription,
            shortDescription = descriptor.pluginShortDescription,
            version = descriptor.version,
            author = descriptor.author,
            license = descriptor.license,
            url = descriptor.pluginUrl,
            hasLogo = hasLogo,
            state = pluginWrapper.pluginState,
            priority = pluginManagementEntry.priority,
            trustLevel = pluginManagementEntry.trustLevel
        )
    }
}