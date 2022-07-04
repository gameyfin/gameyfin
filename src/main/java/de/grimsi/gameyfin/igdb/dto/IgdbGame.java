package de.grimsi.gameyfin.igdb.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class IgdbGame {
    private Long id;
    private List<Long> alternativeNames;
    private Long category;
    private Long cover;
    private Instant createdAt;
    private List<Long> externalGames;
    private Instant firstReleaseDate;
    private Long follows;
    private List<Long> gameModes;
    private List<Long> genres;
    private Long hypes;
    private List<Long> involvedCompanies;
    private List<Long> keywords;
    private List<Long> multiplayerModes;
    private String name;
    private List<Long> platforms;
    private List<Long> playerPerspectives;
    private Float rating;
    private Long ratingCount;
    private List<Long> releaseDates;
    private List<Long> screenshots;
    private List<Long> similiarGames;
    private String slug;
    private String storyline;
    private String summary;
    private List<Long> tags;
}
