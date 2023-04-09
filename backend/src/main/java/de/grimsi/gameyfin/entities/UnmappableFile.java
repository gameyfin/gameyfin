package de.grimsi.gameyfin.entities;

import lombok.*;
import org.hibernate.Hibernate;

import jakarta.persistence.*;

import java.util.Objects;

@Entity
@Getter
@Setter
@ToString
@NoArgsConstructor
public class UnmappableFile {

    public UnmappableFile(String path) {
        this.path = path;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "HIBERNATE_SEQUENCE")
    @SequenceGenerator(name = "HIBERNATE_SEQUENCE", allocationSize = 1)
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
