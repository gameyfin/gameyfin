package de.grimsi.gameyfin.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FilesystemService {
    @Value("${gameyfin.root}")
    private String rootFolderPath;
}
