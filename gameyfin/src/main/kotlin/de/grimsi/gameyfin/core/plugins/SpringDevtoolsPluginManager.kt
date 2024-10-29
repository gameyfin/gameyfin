package de.grimsi.gameyfin.core.plugins

import org.pf4j.CompoundPluginLoader
import org.pf4j.CompoundPluginRepository
import org.pf4j.DefaultPluginLoader
import org.pf4j.DefaultPluginManager
import org.pf4j.DefaultPluginRepository
import org.pf4j.DevelopmentPluginRepository
import org.pf4j.JarPluginLoader
import org.pf4j.JarPluginRepository
import org.pf4j.PluginLoader
import org.pf4j.PluginRepository
import java.nio.file.Path

/**
 * @see https://stackoverflow.com/questions/73654174/my-application-cant-find-the-extension-with-pf4j
 */
class SpringDevtoolsPluginManager(path: Path) : DefaultPluginManager(path) {

    override fun createPluginRepository(): PluginRepository {
        return CompoundPluginRepository()
            .add(DevelopmentPluginRepository(pluginsRoots), this::isDevelopment)
            .add(JarPluginRepository(pluginsRoots), this::isNotDevelopment)
            .add(DefaultPluginRepository(pluginsRoots), this::isNotDevelopment)
    }

    override fun createPluginLoader(): PluginLoader {
        val compoundPluginLoader = CompoundPluginLoader()
        val developmentPluginLoader = SpringDevtoolsDevelopmentPluginLoader(this, javaClass.classLoader)
        val jarPluginLoader = JarPluginLoader(this)
        val defaultPluginLoader = DefaultPluginLoader(this)

        return compoundPluginLoader
            .add(developmentPluginLoader, this::isDevelopment)
            .add(jarPluginLoader, this::isNotDevelopment)
            .add(defaultPluginLoader, this::isNotDevelopment)
    }
}