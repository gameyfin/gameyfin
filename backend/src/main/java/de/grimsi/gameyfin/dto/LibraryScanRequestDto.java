package de.grimsi.gameyfin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LibraryScanRequestDto {
    private String path;
    private boolean downloadImages;
}
