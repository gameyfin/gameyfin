package de.grimsi.gameyfin.service;

import de.grimsi.gameyfin.entities.ConfigProperty;
import de.grimsi.gameyfin.repositories.ConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.Serializable;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConfigService {

    private final ConfigRepository configRepository;

    public boolean check(String key) {
        return configRepository.findById(key).isPresent();
    }

    public <T extends Serializable> T read(String key, Class<T> type) {
        ConfigProperty c = getConfigProperty(key);
        return type.cast(c.getValue());
    }

    public String readString(String key) {
        ConfigProperty c = getConfigProperty(key);
        return c.getValue().toString();
    }

    public Integer readInt(String key) {
        ConfigProperty c = getConfigProperty(key);
        return (Integer) c.getValue();
    }

    public Boolean readBool(String key) {
        ConfigProperty c = getConfigProperty(key);
        return (Boolean) c.getValue();
    }

    public Class<? extends Serializable> getType(String key) {
        ConfigProperty c = getConfigProperty(key);
        return c.getType();
    }

    public ConfigProperty write(String key, Serializable value) {
        ConfigProperty c = ConfigProperty.builder()
                .key(key)
                .value(value)
                .type(value.getClass())
                .build();

        return configRepository.save(c);
    }

    private ConfigProperty getConfigProperty(String key) {
        return configRepository.findById(key).orElseThrow(() -> new RuntimeException("Unknown config key '%s'".formatted(key)));
    }
}
