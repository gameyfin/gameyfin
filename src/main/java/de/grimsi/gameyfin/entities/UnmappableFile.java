package de.grimsi.gameyfin.entities;

import lombok.*;
import org.hibernate.Hibernate;

import javax.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "blacklist")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class UnmappableFile {

    public UnmappableFile(String path) {
        this.path = path;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    private String path;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        UnmappableFile that = (UnmappableFile) o;
        return path != null && Objects.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
