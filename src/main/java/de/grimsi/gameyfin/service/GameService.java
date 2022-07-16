package de.grimsi.gameyfin.service;

import de.grimsi.gameyfin.entities.DetectedGame;
import de.grimsi.gameyfin.entities.UnmappableFile;
import de.grimsi.gameyfin.repositories.UnmappableFileRepository;
import de.grimsi.gameyfin.repositories.DetectedGameRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GameService {

    @Autowired
    private DetectedGameRepository detectedGameRepository;

    @Autowired
    private UnmappableFileRepository unmappableFileRepository;

    public List<DetectedGame> getAllDetectedGames() {
        return detectedGameRepository.findAll();
    }

    public List<UnmappableFile> getAllUnmappedFiles() {
        return unmappableFileRepository.findAll();
    }
}
