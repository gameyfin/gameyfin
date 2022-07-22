package de.grimsi.gameyfin.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GameOverviewDto {
    private String slug;
    private String title;
    private String coverId;
}
