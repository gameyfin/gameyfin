package de.grimsi.gameyfin.entities;

import lombok.*;
import org.hibernate.Hibernate;

import javax.persistence.*;
import java.util.List;
import java.util.Objects;

@Entity
@Builder
@Getter
@Setter
@ToString
@AllArgsConstructor
@RequiredArgsConstructor
public class Library {

    @Id
    private String path;

    @ManyToMany(cascade = CascadeType.MERGE)
    @ToString.Exclude
    private List<Platform> platforms;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Library library = (Library) o;
        return path != null && Objects.equals(path, library.path);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
