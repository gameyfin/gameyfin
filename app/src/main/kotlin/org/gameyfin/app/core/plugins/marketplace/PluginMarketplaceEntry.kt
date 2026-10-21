package org.gameyfin.app.core.plugins.marketplace

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * A plugin marketplace is a (git) repository containing a `plugins.json` index of available plugins.
 */
@Entity
@Table(name = "plugin_marketplace_entry")
class PluginMarketplaceEntry(
    @Id
    val id: String,

    @Column(nullable = false)
    var name: String,

    /**
     * The URL as entered by the user (e.g. a GitHub repository URL)
     */
    @Column(nullable = false, length = 2048)
    var url: String,

    /**
     * The resolved URL of the `plugins.json` index file
     */
    @Column(name = "index_url", nullable = false, length = 2048)
    var indexUrl: String,

    @Column(nullable = false)
    var enabled: Boolean = true,

    @Column(nullable = false)
    val official: Boolean = false,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
