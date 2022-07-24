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
        return FilenameUtils.getBaseName(p.toString());
    }

    public static String getFilenameWithExtension(Path p) {
        return FilenameUtils.getName(p.toString());
    }

    public static boolean hasGameArchiveExtension(Path p) {
        return possibleGameFileExtensions.contains(FilenameUtils.getExtension(p.getFileName().toString()));
    }

}
