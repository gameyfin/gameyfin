package de.grimsi.gameyfin.core.plugins.management

import org.pf4j.DefaultPluginDescriptor
import org.pf4j.PluginDescriptor

data class GameyfinPluginDescriptor(
    var pluginUrl: String,
    var pluginName: String,
    var author: String
) : DefaultPluginDescriptor() {

    constructor(
        descriptor: PluginDescriptor,
        url: String,
        name: String,
        author: String
    ) : this(
        pluginUrl = url,
        pluginName = name,
        author = author
    ) {
        this.pluginId = descriptor.pluginId
        // The Manifest parser ignores newlines in the description
        this.pluginDescription = descriptor.pluginDescription.replace("<br>", "\n")
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