package de.grimsi.gameyfin.entities;


import lombok.*;
import org.hibernate.Hibernate;

import javax.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Entity
@Builder
@Getter
@Setter
@ToString
@RequiredArgsConstructor
public class DetectedGame {

    // Game properties
    @Id
    private String slug;

    @Column(nullable = false)
    private String title;

    private String summary;

    private Instant releaseDate;

    private Integer userRating;

    private Integer criticsRating;

    private Integer totalRating;

    @ManyToOne
    private Category category;

    private boolean offlineCoop;

    private boolean onlineCoop;

    private boolean lanSupport;

    private int maxPlayers;

    @Column(nullable = false)
    private Long coverId;

    @ElementCollection
    private List<Long> screenshotIds;

    @ElementCollection
    private List<Long> videoIds;

    @ManyToMany
    @ToString.Exclude
    private List<Company> companies;

    @ManyToMany
    @ToString.Exclude
    private List<Genre> genres;

    @ManyToMany
    @ToString.Exclude
    private List<Keyword> keywords;

    @ManyToMany
    @ToString.Exclude
    private List<Theme> themes;

    @ManyToMany
    @ToString.Exclude
    private List<PlayerPerspective> playerPerspectives;

    // Technical properties
    @Column(nullable = false)
    private String path;

    @Column(nullable = false)
    private boolean isFolder;

    @Column(columnDefinition = "boolean default false")
    private boolean confirmedMatch;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        DetectedGame that = (DetectedGame) o;
        return slug != null && Objects.equals(slug, that.slug);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
