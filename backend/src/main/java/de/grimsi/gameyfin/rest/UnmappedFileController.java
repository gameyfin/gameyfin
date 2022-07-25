package de.grimsi.gameyfin.rest;

import de.grimsi.gameyfin.entities.DetectedGame;
import de.grimsi.gameyfin.entities.UnmappableFile;
import de.grimsi.gameyfin.service.GameService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/unmapped-files")
@RequiredArgsConstructor
public class UnmappedFileController {

    private final GameService gameService;

}
