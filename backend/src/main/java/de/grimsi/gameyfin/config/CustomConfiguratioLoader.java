package de.grimsi.gameyfin.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.util.Objects;
import java.util.Properties;

@Configuration
public class CustomConfiguratioLoader {

    @Autowired
    public void setConfigurableEnvironment(ConfigurableEnvironment env) {
        try {
            String firstLibraryPath = env.resolvePlaceholders("gameyfin.root").split(",")[0];
            Properties props = new Properties();
            props.setProperty("gameyfin.db", "%s/.gameyfin/db".formatted(firstLibraryPath));
            props.setProperty("gameyfin.cache", "%s/.gameyfin/cache".formatted(firstLibraryPath));
            env.getPropertySources().addFirst(new PropertiesPropertySource("dynamicallyLoadedGameyfinProperties", props));

            Resource resource = new ClassPathResource("/config/secure.yml");
            env.getPropertySources().addFirst(new PropertiesPropertySource(Objects.requireNonNull(resource.getFilename()), PropertiesLoaderUtils.loadProperties(resource)));
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage(), ex);
        }
    }
}
