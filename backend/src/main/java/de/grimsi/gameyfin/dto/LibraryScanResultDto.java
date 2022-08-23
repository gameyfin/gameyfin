package de.grimsi.gameyfin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LibraryScanResultDto {
    private int newGames;
    private int deletedGames;
    private int newUnmappableFiles;
    private int totalGames;
    private int coverDownloads;
    private int screenshotDownloads;
    private int companyLogoDownloads;
    private int scanDuration;
}
