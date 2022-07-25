package de.grimsi.gameyfin.util;

import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
public class FilenameUtil {

    private static List<String> possibleGameFileExtensions;

    @Value("${gameyfin.file-extensions}")
    public void setPossibleGameFileExtensions(List<String> possibleGameFileExtensions) {
        FilenameUtil.possibleGameFileExtensions = possibleGameFileExtensions;
    }

    public static String getFilenameWithoutExtension(Path p) {

        // If the path points to a folder, return the folder name
        // Folders like "Counter Strike 1.6" would otherwise be returned as "Counter Strike 1"
        if(p.toFile().isDirectory()) return FilenameUtils.getName(p.toString());

        return FilenameUtils.getBaseName(p.toString());
    }

    public static String getFilenameWithExtension(Path p) {
        return FilenameUtils.getName(p.toString());
    }

    public static boolean hasGameArchiveExtension(Path p) {
        return possibleGameFileExtensions.contains(FilenameUtils.getExtension(p.getFileName().toString()));
    }

}
