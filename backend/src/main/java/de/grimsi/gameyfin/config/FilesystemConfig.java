package de.grimsi.gameyfin.config;

import de.grimsi.gameyfin.service.FilesystemService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;

/**
 * This class contains logic to the configuration of the filesystem which Gameyfin works on.
 * It handles creating required folders, setting up paths for Gameyfin and setting the correct properties of those folders.
 */
@Configuration
@RequiredArgsConstructor
public class FilesystemConfig {

    @Value("${gameyfin.folders.data}")
    private String dataFolderPath;

    private final FilesystemService filesystemService;

    /**
     * This will create the cache folder for Gameyfin.
     * The path of this folder is specified in the "gameyfin.cache" parameter which is either:
     * 1. Derived from the first configured library folder path or
     * 2. Explicitly set by the user
     * <p>
     * For more details see {@link GameyfinFolderConfig#setConfigurableEnvironment(ConfigurableEnvironment)}
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(1)
    public void createCacheFolder() {
        filesystemService.createCacheFolder();
    }

    /**
     * This will make sure that the internal folder (".gameyfin") is marked as hidden on DOS/Windows-based systems.
     * On UNIX-based systems files and folders starting with a dot are hidden
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(2)
    public void hideInternalFolderOnDOS() throws IOException {
        if (!isRunningOnWindows()) return;

        Path internalFolder = filesystemService.getPath(dataFolderPath);

        if (!Files.exists(internalFolder) || !Files.isDirectory(internalFolder)) return;

        Files.setAttribute(internalFolder, "dos:hidden", Boolean.TRUE, LinkOption.NOFOLLOW_LINKS);
    }

    private boolean isRunningOnWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("windows");
    }
}
