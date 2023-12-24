package de.grimsi.gameyfin.service;

import de.grimsi.gameyfin.config.properties.ConfigKey;
import de.grimsi.gameyfin.entities.ConfigProperty;
import de.grimsi.gameyfin.repositories.ConfigRepository;
import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.Serializable;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfigServiceTest {

    @Mock
    private ConfigRepository configRepository;

    @InjectMocks
    private ConfigService target;

    private final EasyRandom easyRandom = new EasyRandom();

    @Test
    void hasValue_PresentNotEmpty() {
        ConfigProperty c = randomConfigProperty(String.class);

        when(configRepository.findById(eq(c.getKey()))).thenReturn(Optional.of(c));

        assertThat(target.hasValue(ConfigKey.valueOf(c.getKey()))).isTrue();
    }

    @Test
    void hasValue_PresentEmpty() {
        ConfigProperty c = randomConfigProperty(String.class);
        c.setValue("");

        when(configRepository.findById(eq(c.getKey()))).thenReturn(Optional.of(c));

        assertThat(target.hasValue(ConfigKey.valueOf(c.getKey()))).isFalse();
    }

    @Test
    void hasValue_NotPresent() {
        ConfigKey key = easyRandom.nextObject(ConfigKey.class);

        when(configRepository.findById(eq(key.name()))).thenReturn(Optional.empty());

        assertThat(target.hasValue(key)).isFalse();
    }

    @Test
    void read() {
        ConfigProperty c = randomConfigProperty(String.class);

        when(configRepository.findById(c.getKey())).thenReturn(Optional.of(c));

        String result = target.read(ConfigKey.valueOf(c.getKey()), String.class);

        assertThat(result.getClass()).isEqualTo(String.class);
        assertThat(result).isEqualTo(c.getValue());
    }

    @Test
    void read_WrongType() {
        ConfigProperty c = randomConfigProperty(String.class);

        when(configRepository.findById(c.getKey())).thenReturn(Optional.of(c));

        assertThrows(ClassCastException.class, () -> target.read(ConfigKey.valueOf(c.getKey()), Integer.class));
    }

    @Test
    void readString() {
        ConfigProperty c = randomConfigProperty(String.class);

        when(configRepository.findById(c.getKey())).thenReturn(Optional.of(c));

        String result = target.readString(ConfigKey.valueOf(c.getKey()));

        assertThat(result.getClass()).isEqualTo(String.class);
        assertThat(result).isEqualTo(c.getValue());
    }

    @Test
    void readInt() {
        ConfigProperty c = randomConfigProperty(Integer.class);

        when(configRepository.findById(c.getKey())).thenReturn(Optional.of(c));

        Integer result = target.readInt(ConfigKey.valueOf(c.getKey()));

        assertThat(result.getClass()).isEqualTo(Integer.class);
        assertThat(result).isEqualTo(Integer.valueOf(c.getValue()));
    }

    @Test
    void readBool() {
        ConfigProperty c = randomConfigProperty(Boolean.class);

        when(configRepository.findById(c.getKey())).thenReturn(Optional.of(c));

        Boolean result = target.readBool(ConfigKey.valueOf(c.getKey()));

        assertThat(result.getClass()).isEqualTo(Boolean.class);
        assertThat(result).isEqualTo(Boolean.valueOf(c.getValue()));
    }

    @Test
    void getType() {
        ConfigProperty c = randomConfigProperty(String.class);

        when(configRepository.findById(c.getKey())).thenReturn(Optional.of(c));

        Serializable result = target.getType(ConfigKey.valueOf(c.getKey()));

        assertThat(result).isEqualTo(String.class);
    }

    @Test
    void write() {
        ConfigProperty c = randomConfigProperty(String.class);

        when(configRepository.save(eq(c))).thenReturn(c);

        ConfigProperty result = target.write(ConfigKey.valueOf(c.getKey()), c.getValue());

        verify(configRepository, times(1)).save(eq(c));
        assertThat(result).isEqualTo(c);
    }

    private ConfigProperty randomConfigProperty(Class<? extends Serializable> type) {
        return ConfigProperty.builder()
                .key(easyRandom.nextObject(ConfigKey.class).name())
                .value(easyRandom.nextObject(type).toString())
                .type(type)
                .build();
    }
}
