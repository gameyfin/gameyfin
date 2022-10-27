package de.grimsi.gameyfin.dto;

import lombok.Data;

@Data
public class LibraryScanRequestDto {
    private String path;
    private boolean downloadImages;
}
