package de.grimsi.gameyfin.entities;

import lombok.*;
import org.hibernate.Hibernate;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "blacklist")
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class BlacklistEntry {
    @Id
    private String path;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        BlacklistEntry that = (BlacklistEntry) o;
        return path != null && Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
