package de.grimsi.gameyfin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GameDto {
    private String name;
    private String publisher;
    private String slug;
    private Instant releaseDate;

    private List<File> files;
    private Long fileSize;
}
