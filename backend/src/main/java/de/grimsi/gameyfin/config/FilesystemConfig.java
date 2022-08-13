package de.grimsi.gameyfin.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.*;
import org.springframework.util.PropertyPlaceholderHelper;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.Properties;
import java.util.stream.StreamSupport;

@Configuration
public class FilesystemConfig {

    @Value("#{'${gameyfin.root}'.split(',')[0]}")
    private String firstLibraryPath;

    @Autowired
    Environment env;

    @Autowired
    public void setConfigurableEnvironment(ConfigurableEnvironment env) {
        Properties props = new Properties();
        props.setProperty("gameyfin.db", "%s/.gameyfin/db".formatted(firstLibraryPath));
        props.setProperty("gameyfin.cache", "%s/.gameyfin/cache".formatted(firstLibraryPath));
        env.getPropertySources().addFirst(new PropertiesPropertySource("gameyfinFilesystemProperties", props));
    }

    /**
     * This bean is needed so Spring initializes the data source after we are done messing with the configuration environment
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
