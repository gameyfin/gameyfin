package de.grimsi.gameyfin.entities;

import lombok.*;
import org.hibernate.Hibernate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.Objects;

@Entity
@Builder
@Getter
@Setter
@ToString
@AllArgsConstructor
@RequiredArgsConstructor
public class Platform {
    @Id
    private String slug;

    @Column(nullable = false)
    private String name;

    private String logoId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Platform platform = (Platform) o;
        return slug != null && Objects.equals(slug, platform.slug);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
