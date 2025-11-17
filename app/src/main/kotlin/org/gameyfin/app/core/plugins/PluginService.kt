package org.gameyfin.app.core.plugins

import io.github.oshai.kotlinlogging.KotlinLogging
import org.gameyfin.app.core.plugins.config.PluginConfigEntry
import org.gameyfin.app.core.plugins.config.PluginConfigEntryKey
import org.gameyfin.app.core.plugins.config.PluginConfigRepository
import org.gameyfin.app.core.plugins.dto.PluginConfigMetadataDto
import org.gameyfin.app.core.plugins.dto.PluginDto
import org.gameyfin.app.core.plugins.dto.PluginUpdateDto
import org.gameyfin.app.core.plugins.management.GameyfinPluginDescriptor
import org.gameyfin.app.core.plugins.management.GameyfinPluginManager
import org.gameyfin.app.core.plugins.management.PluginManagementEntry
import org.gameyfin.app.core.plugins.management.PluginManagementRepository
import org.gameyfin.pluginapi.core.config.Configurable
import org.gameyfin.pluginapi.core.config.PluginConfigValidationResult
import org.gameyfin.pluginapi.core.wrapper.GameyfinPlugin
import org.pf4j.ExtensionPoint
import org.pf4j.PluginState
import org.pf4j.PluginWrapper
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.toJavaDuration

@Service
class PluginService(
    private val pluginManager: GameyfinPluginManager,
    private val pluginManagementRepository: PluginManagementRepository,
    private val pluginConfigRepository: PluginConfigRepository
) {
    companion object {
        private val log = KotlinLogging.logger {}

        /* Websockets */
        private val pluginUpdates = Sinks.many().multicast().onBackpressureBuffer<PluginUpdateDto>(1024, false)

        fun subscribe(): Flux<List<PluginUpdateDto>> {
            log.debug { "New subscription for pluginUpdates" }
            return pluginUpdates.asFlux()
                .buffer(100.milliseconds.toJavaDuration())
                .doOnSubscribe { log.debug { "Subscriber added to pluginUpdates [${pluginUpdates.currentSubscriberCount()}]" } }
                .doFinally {
                    log.debug { "Subscriber removed from pluginUpdates with signal type $it [${pluginUpdates.currentSubscriberCount()}]" }
                }
        }

        fun emit(update: PluginUpdateDto) {
            pluginUpdates.tryEmitNext(update)
        }
    }

    private val pluginConfigValidationCache = mutableMapOf<String, PluginConfigValidationResult>()

    init {
        pluginManager.addPluginStateListener { event ->
            val update = PluginUpdateDto(id = event.plugin.pluginId, state = event.pluginState)
            emit(update)
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

    fun getPluginManagementEntry(clazz: Class<out ExtensionPoint>): PluginManagementEntry {
        val pluginWrapper = pluginManager.whichPlugin(clazz)
        return pluginManagementRepository.findByIdOrNull(pluginWrapper.pluginId)
            ?: throw IllegalArgumentException("Plugin with class $clazz not found")
    }

    fun getPluginManagementEntries(
        type: Class<out ExtensionPoint>,
        enabledOnly: Boolean = true
    ): List<PluginManagementEntry> {
        val pluginWrappers = if (enabledOnly) {
            pluginManager.plugins.filter { it.pluginState == PluginState.STARTED }
        } else {
            pluginManager.plugins
        }

        return pluginWrappers.filter { pluginManager.getExtensionTypes(it.pluginId).contains(type.simpleName) }
            .map {
                pluginManagementRepository.findByIdOrNull(it.pluginId)
                    ?: throw IllegalArgumentException("Plugin with id ${it.pluginId} not found")
            }
    }

    fun enablePlugin(pluginId: String) {
        pluginManager.enablePlugin(pluginId)
    }

    fun disablePlugin(pluginId: String) {
        pluginManager.disablePlugin(pluginId)
    }

    fun setPluginPriorities(pluginPriorities: Map<String, Int>) {
        pluginPriorities.forEach { (pluginId, priority) ->
            val pluginManagementEntry = pluginManager.getManagementEntry(pluginId)
            pluginManagementEntry.priority = priority
            pluginManagementRepository.save(pluginManagementEntry)
        }
    }

    fun getLogo(pluginId: String): ByteArray? {
        val plugin = pluginManager.getPlugin(pluginId).plugin as GameyfinPlugin
        return plugin.getLogo()
    }

    fun getConfigMetadata(pluginWrapper: PluginWrapper): List<PluginConfigMetadataDto>? {
        log.debug { "Getting config metadata for plugin ${pluginWrapper.pluginId}" }
        val plugin = pluginWrapper.plugin

        if (plugin !is Configurable) return null

        return plugin.configMetadata.map { meta ->
            PluginConfigMetadataDto(
                key = meta.key,
                type = meta.type.simpleName ?: "Unknown",
                label = meta.label,
                description = meta.description,
                default = meta.default,
                secret = meta.isSecret,
                required = meta.isRequired,
                allowedValues = meta.allowedValues?.map { it.toString() }
            )
        }
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
        emit(update)
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
        val pluginManagementEntry = pluginManager.getManagementEntry(pluginWrapper.pluginId)

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