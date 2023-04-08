package de.grimsi.gameyfin.entities;

import lombok.*;
import org.hibernate.Hibernate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.util.Objects;

@Entity
@Builder
@Getter
@Setter
@ToString
@AllArgsConstructor
@RequiredArgsConstructor
public class Company {
    @Id
    private String slug;

    @Column(nullable = false)
    private String name;

    private String logoId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        Company company = (Company) o;
        return slug != null && Objects.equals(slug, company.slug);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
