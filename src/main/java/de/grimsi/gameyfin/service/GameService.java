package de.grimsi.gameyfin.service;

import de.grimsi.gameyfin.entities.DetectedGame;
import de.grimsi.gameyfin.repositories.BlacklistRepository;
import de.grimsi.gameyfin.repositories.DetectedGameRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GameService {

    @Autowired
    private DetectedGameRepository detectedGameRepository;

    @Autowired
    private BlacklistRepository blacklistRepository;

    public List<DetectedGame> getAllDetectedGames() {
        return detectedGameRepository.findAll();
    }
}
