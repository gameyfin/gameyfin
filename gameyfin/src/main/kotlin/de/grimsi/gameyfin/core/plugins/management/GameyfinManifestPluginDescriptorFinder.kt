package de.grimsi.gameyfin.core.plugins.management

import org.pf4j.ManifestPluginDescriptorFinder
import java.util.jar.Manifest

class GameyfinManifestPluginDescriptorFinder() : ManifestPluginDescriptorFinder() {

    companion object {
        const val PLUGIN_NAME: String = "Plugin-Name"
        const val PLUGIN_AUTHOR: String = "Plugin-Author"
        const val PLUGIN_SHORT_DESCRIPTION: String = "Plugin-Short-Description"
        const val PLUGIN_URL: String = "Plugin-Url"
    }

    override fun createPluginDescriptor(manifest: Manifest?): GameyfinPluginDescriptor {
        if (manifest == null) throw IllegalArgumentException("Manifest cannot be null")

        val pluginDescriptor = super.createPluginDescriptor(manifest)

        val attributes = manifest.mainAttributes

        return GameyfinPluginDescriptor(
            descriptor = pluginDescriptor,
            name = attributes.getValue(PLUGIN_NAME)
                ?: throw IllegalStateException("Plugin-Name not found in manifest"),
            shortDescription = attributes.getValue(PLUGIN_SHORT_DESCRIPTION),
            author = attributes.getValue(PLUGIN_AUTHOR)
                ?: throw IllegalStateException("Plugin-Author not found in manifest"),
            url = attributes.getValue(PLUGIN_URL),
        )
    }
}