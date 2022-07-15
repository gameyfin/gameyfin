package de.grimsi.gameyfin.entities;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.Hibernate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import java.util.Objects;

@Entity
@Getter
@Setter
@ToString
@RequiredArgsConstructor
public class Company {
    @Id
    private String slug;

    @Column(nullable = false)
    private String name;

    private Long logoId;

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
