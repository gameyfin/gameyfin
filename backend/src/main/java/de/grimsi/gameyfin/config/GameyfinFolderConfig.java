package de.grimsi.gameyfin.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.*;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.StreamSupport;

/**
 * This class handles the creation of all folders used by Gameyfin.
 * It also stores their paths in Spring environment variables.
 */
@Configuration
@RequiredArgsConstructor
public class GameyfinFolderConfig {

    private static final String INTERNAL_FOLDER_NAME = ".gameyfin";

    /**
     * The following SpEL expression will:
     * 1. Split the comma-seperated string contained in "gameyfin.sources" into elements
     * 2. Take the first element
     * 3. Assign its value to the variable
     */
    @Value("#{'${gameyfin.sources}'.split(',')[0]}")
    private String firstLibraryPath;

    @Value("${gameyfin.folders.data}")
    private String dataFolderPath;

    private final Environment env;

    /**
     * Dynamically sets the "gameyfin.db" and "gameyfin.cache" properties
     * if the "gameyfin.folders.data" property is *not* set by the user (default).
     *
     * @param env - The application environment, provided by the Spring container
     */
    @Autowired
    public void setConfigurableEnvironment(ConfigurableEnvironment env) {
        Properties props = new Properties();

        if (!StringUtils.hasText(dataFolderPath)) {

            //set the data folder property, so it can be referenced at runtime
            props.setProperty("gameyfin.folders.data", "%s/%s".formatted(firstLibraryPath, INTERNAL_FOLDER_NAME));

            props.setProperty("gameyfin.db", "%s/%s/db".formatted(firstLibraryPath, INTERNAL_FOLDER_NAME));
            props.setProperty("gameyfin.cache", "%s/%s/cache".formatted(firstLibraryPath, INTERNAL_FOLDER_NAME));
        } else {

            props.setProperty("gameyfin.db", "%s/%s/db".formatted(dataFolderPath, INTERNAL_FOLDER_NAME));
            props.setProperty("gameyfin.cache", "%s/%s/cache".formatted(dataFolderPath, INTERNAL_FOLDER_NAME));
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
}
