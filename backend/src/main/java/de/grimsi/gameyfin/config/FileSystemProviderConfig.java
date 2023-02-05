package de.grimsi.gameyfin.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;

/**
 * This class holds configuration for the default {@link java.nio.file.spi.FileSystemProvider} used by Gameyfin.
 */
@Configuration
public class FileSystemProviderConfig {
    /**
     * Configures the default {@link FileSystem} to be used.
     * This makes it easier to mock certain classes in unit tests.
     * @return the default FileSystem
     */
    @Bean
    public FileSystem defaultFileSystem() {
        return FileSystems.getDefault();
    }
}
