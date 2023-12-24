package de.grimsi.gameyfin.service;

import de.grimsi.gameyfin.config.properties.ConfigKey;
import de.grimsi.gameyfin.entities.ConfigProperty;
import de.grimsi.gameyfin.repositories.ConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConfigService {

    private final ConfigRepository configRepository;

    public boolean hasValue(ConfigKey key) {
        Optional<ConfigProperty> o = configRepository.findById(key.name());

        if (o.isEmpty()) return false;

        ConfigProperty c = o.get();

        return hasContent(c.getValue());
    }

    public <T extends Serializable> T read(ConfigKey key, Class<T> type) {
        ConfigProperty c = getConfigProperty(key);
        return type.cast(c.getValue());
    }

    public String readString(ConfigKey key) {
        ConfigProperty c = getConfigProperty(key);
        return c.getValue();
    }

    public Integer readInt(ConfigKey key) {
        ConfigProperty c = getConfigProperty(key);
        return Integer.valueOf(c.getValue());
    }

    public Boolean readBool(ConfigKey key) {
        ConfigProperty c = getConfigProperty(key);
        return Boolean.valueOf(c.getValue());
    }

    public Class<? extends Serializable> getType(ConfigKey key) {
        ConfigProperty c = getConfigProperty(key);
        return c.getType();
    }

    public ConfigProperty write(ConfigKey key, Serializable value) {
        ConfigProperty c = ConfigProperty.builder()
                .key(key.name())
                .value(value.toString())
                .type(value.getClass())
                .build();

        return configRepository.save(c);
    }

    private ConfigProperty getConfigProperty(ConfigKey key) {
        return configRepository.findById(key.name()).orElseThrow(() -> new RuntimeException("Unknown config key '%s'".formatted(key)));
    }

    private boolean hasContent(Serializable value) {
        return !value.toString().isEmpty();
    }
}
