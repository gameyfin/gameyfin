package de.grimsi.gameyfin.core.plugins.management

import org.pf4j.DefaultPluginDescriptor
import org.pf4j.PluginDescriptor

data class GameyfinPluginDescriptor(
    var pluginUrl: String?,
    var pluginName: String,
    var pluginShortDescription: String?,
    var author: String
) : DefaultPluginDescriptor() {

    companion object {
        const val NEWLINE_INDICATOR = "<br>"
    }

    constructor(
        descriptor: PluginDescriptor,
        url: String?,
        name: String,
        shortDescription: String?,
        author: String
    ) : this(
        pluginUrl = url,
        pluginName = name,
        pluginShortDescription = shortDescription,
        author = author
    ) {
        this.pluginId = descriptor.pluginId
        // The Manifest spec does not account for line breaks in values
        this.pluginDescription = descriptor.pluginDescription.replace(NEWLINE_INDICATOR, "\n")
        this.pluginClass = descriptor.pluginClass
        this.setPluginVersion(descriptor.version)
        this.requires = descriptor.requires
        this.license = descriptor.license

        // Use reflection to access the private 'dependencies' field
        // This is because the internal (List<PluginDependency>) and external (List<String>) representation of the field differ
        this.javaClass.superclass.getDeclaredField("dependencies").let {
            it.isAccessible = true
            it.set(this, descriptor.dependencies)
        }
    }

    override fun getProvider() = author
}