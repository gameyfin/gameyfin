package de.grimsi.gameyfin.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.*;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.StreamSupport;

@Configuration
public class FilesystemConfig {

    private static final String INTERNAL_FOLDER_NAME = ".gameyfin";

    @Value("#{'${gameyfin.sources}'.split(',')[0]}")
    private String firstLibraryPath;

    @Value("${gameyfin.db}")
    private String dbPath;

    @Value("${gameyfin.cache}")
    private String cachePath;

    @Value("${gameyfin.torrent}")
    private String torrentPath;

    @Autowired
    Environment env;

    /**
     * This will make sure that the internal folder (".gameyfin") is marked as hidden on DOS/Windows-based systems.
     * On UNIX-based systems files and folders starting with a dot are hidden
     */
    @EventListener(ApplicationReadyEvent.class)
    public void hideInternalFolderOnDOS() throws IOException {
        if (!isRunningOnWindows()) return;

        Path internalFolder = Paths.get("%s/%s".formatted(firstLibraryPath, INTERNAL_FOLDER_NAME));

        if (!Files.exists(internalFolder) || !Files.isDirectory(internalFolder)) return;

        Files.setAttribute(internalFolder, "dos:hidden", Boolean.TRUE, LinkOption.NOFOLLOW_LINKS);
    }

    @Autowired
    public void setConfigurableEnvironment(ConfigurableEnvironment env) {
        Properties props = new Properties();

        if (!StringUtils.hasText(dbPath)) {
            props.setProperty("gameyfin.db", "%s/%s/db".formatted(firstLibraryPath, INTERNAL_FOLDER_NAME));
        }

        if (!StringUtils.hasText(cachePath)) {
            props.setProperty("gameyfin.cache", "%s/%s/cache".formatted(firstLibraryPath, INTERNAL_FOLDER_NAME));
        }

        env.getPropertySources().addFirst(new PropertiesPropertySource("gameyfinFilesystemProperties", props));
    }

    /**
     * This bean is needed so Spring initializes the data source after we are done messing with the configuration environment
     *
     * @return DataSource
     */
    @ConfigurationProperties(prefix = "spring.datasource")
    @Bean
    @Primary
    public DataSource getDataSource() {

        Properties properties = loadAllProperties();

        return DataSourceBuilder
                .create()
                .url(properties.getProperty("spring.datasource.url"))
                .build();
    }

    private Properties loadAllProperties() {
        Properties props = new Properties();

        MutablePropertySources propSrcs = ((AbstractEnvironment) env).getPropertySources();

        StreamSupport.stream(propSrcs.spliterator(), false)
                .filter(ps -> ps instanceof EnumerablePropertySource)
                .map(ps -> ((EnumerablePropertySource<?>) ps).getPropertyNames())
                .flatMap(Arrays::stream)
                .forEach(propName -> props.setProperty(propName, env.getProperty(propName)));

        return props;
    }

    private boolean isRunningOnWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("windows");
    }

}
