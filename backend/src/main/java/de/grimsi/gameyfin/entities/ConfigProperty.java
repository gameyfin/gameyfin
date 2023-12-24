package de.grimsi.gameyfin.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Builder
@Getter
@Setter
@ToString
@AllArgsConstructor
@RequiredArgsConstructor
@Table(name = "gameyfin_config")
public class ConfigProperty {
    @Id
    @Column(name = "config_key") //"key" is a reserved word in H2
    private String key;

    @Column(name = "config_value") //"value" is a reserved word in H2
    private String value;

    private Class<? extends Serializable> type;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfigProperty that = (ConfigProperty) o;
        return Objects.equals(key, that.key) && Objects.equals(value, that.value) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value, type);
    }
}
