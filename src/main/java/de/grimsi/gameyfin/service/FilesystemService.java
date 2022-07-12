package de.grimsi.gameyfin.service;

import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class FilesystemService {
    @Value("${gameyfin.root}")
    private String rootFolderPath;

    @Value("${gameyfin.file-extensions}")
    private List<String> possibleGameFileExtensions;

    public List<Path> getGameFiles() {
        Path rootFolder = Path.of(rootFolderPath);

        try(Stream<Path> stream = Files.list(rootFolder)) {
            // return all sub-folders (non-recursive) and files that have an extension that indicates that they are a downloadable file
            return stream.filter(p -> Files.isDirectory(p) || possibleGameFileExtensions.contains(FilenameUtils.getExtension(p.getFileName().toString()))).toList();
        } catch (IOException e) {
            throw new RuntimeException("Error while opening root folder", e);
        }
    }

    public List<String> getGameFileNames() {
        return this.getGameFiles().stream().map(p -> FilenameUtils.getBaseName(p.toString())).toList();
    }
}
