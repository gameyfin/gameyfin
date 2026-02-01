package org.gameyfin.app.core.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.coyote.ProtocolHandler
import org.apache.coyote.http11.AbstractHttp11Protocol
import org.springframework.boot.tomcat.TomcatProtocolHandlerCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Tomcat configuration to optimize for concurrent connections
 * and prevent download operations from blocking the server.
 */
@Configuration
class TomcatConfig {

    companion object {
        private val log = KotlinLogging.logger { }
    }

    @Bean
    fun protocolHandlerCustomizer(): TomcatProtocolHandlerCustomizer<*> {
        return TomcatProtocolHandlerCustomizer { protocolHandler: ProtocolHandler ->
            if (protocolHandler is AbstractHttp11Protocol<*>) {
                // Increase max connections to handle more concurrent users
                protocolHandler.maxConnections = 10000

                // Increase max threads to handle more concurrent requests
                protocolHandler.maxThreads = 200

                // Set minimum spare threads
                protocolHandler.minSpareThreads = 10

                // Set connection timeout (20 seconds)
                protocolHandler.connectionTimeout = 20000

                // Keep alive settings to reuse connections
                protocolHandler.keepAliveTimeout = 60000
                protocolHandler.maxKeepAliveRequests = 100

                log.debug {
                    "Configured Tomcat connector: maxConnections=${protocolHandler.maxConnections}, " +
                            "maxThreads=${protocolHandler.maxThreads}, " +
                            "minSpareThreads=${protocolHandler.minSpareThreads}"
                }
            }
        }
    }
}

