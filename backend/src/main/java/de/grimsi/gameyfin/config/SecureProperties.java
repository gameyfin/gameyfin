package de.grimsi.gameyfin.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.util.Objects;

@Configuration
public class SecureProperties {

    @Autowired
    public void setConfigurableEnvironment(ConfigurableEnvironment env) {
        try {
            Resource resource = new ClassPathResource("/config/secure.properties");
            env.getPropertySources().addFirst(new PropertiesPropertySource(Objects.requireNonNull(resource.getFilename()), PropertiesLoaderUtils.loadProperties(resource)));
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage(), ex);
        }
    }
}
