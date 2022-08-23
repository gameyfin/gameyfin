package de.grimsi.gameyfin.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LibraryScanResult {
    private int newGames;
    private int deletedGames;
    private int newUnmappableFiles;
    private int totalGames;
}
