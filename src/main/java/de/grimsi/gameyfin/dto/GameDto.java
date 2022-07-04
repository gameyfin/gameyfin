package de.grimsi.gameyfin.dto;

import lombok.Builder;
import lombok.Data;

import java.io.File;
import java.time.Instant;
import java.util.List;

@Data
@Builder
public class GameDto {
    private String name;
    private String publisher;
    private Long igdbGameId;
    private Instant releaseDate;

    private List<File> files;
    private Long fileSize;
}
