package de.grimsi.gameyfin.dto;

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
public class AutocompleteSuggestionDto {
    private String slug;
    private String title;
    private Instant releaseDate;
    private List<String> platforms;
}
