package de.grimsi.gameyfin.entities;


import lombok.*;
import org.hibernate.Hibernate;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Entity
@Builder
@Getter
@Setter
@ToString
@AllArgsConstructor
@RequiredArgsConstructor
public class DetectedGame {

    // Game properties
    @Id
    private String slug;

    @Column(nullable = false)
    private String title;

    @Lob
    @Column(columnDefinition="CLOB")
    private String summary;

    private Instant releaseDate;

    private Integer userRating;

    private Integer criticsRating;

    private Integer totalRating;

    private String category;

    private boolean offlineCoop;

    private boolean onlineCoop;

    private boolean lanSupport;

    private int maxPlayers;

    @Column(nullable = false)
    private String coverId;

    @ElementCollection
    private List<String> screenshotIds;

    @ElementCollection
    private List<String> videoIds;

    @ManyToMany(cascade = CascadeType.MERGE)
    @ToString.Exclude
    private List<Company> companies;

    @ManyToMany(cascade = CascadeType.MERGE)
    @ToString.Exclude
    private List<Genre> genres;

    @ManyToMany(cascade = CascadeType.MERGE)
    @ToString.Exclude
    private List<Keyword> keywords;

    @ManyToMany(cascade = CascadeType.MERGE)
    @ToString.Exclude
    private List<Theme> themes;

    @ManyToMany(cascade = CascadeType.MERGE)
    @ToString.Exclude
    private List<PlayerPerspective> playerPerspectives;

    @ManyToMany(cascade = CascadeType.MERGE)
    @ToString.Exclude
    private List<Platform> platforms;

    @ManyToOne
    @JoinColumn(name = "library")
    private Library library;

    // Technical properties
    @Column(nullable = false)
    private String path;

    @Column(nullable = false)
    private long diskSize;

    @Column(columnDefinition = "boolean default false")
    private boolean confirmedMatch;

    @CreationTimestamp
    private Instant addedToLibrary;

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
