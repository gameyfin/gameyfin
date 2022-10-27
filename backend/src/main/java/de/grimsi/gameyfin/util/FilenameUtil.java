package de.grimsi.gameyfin.util;

import org.apache.commons.io.FilenameUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FilenameUtil {

    private static List<String> possibleGameFileExtensions;
    private static List<String> possibleGameFileSuffixes;
    // matches v1.1.1 v1.1 v1 version numbers
    private static final Pattern versionPattern = Pattern.compile("v(\\d+\\.)?(\\d+\\.)?(\\d+)");
    private static final Pattern trailingNoisePattern = Pattern.compile("( |\\(\\)|[-_.])+$");
    private static final Pattern headingNoisePattern = Pattern.compile("^( |\\(\\)|[-_.])+");

    @Value("${gameyfin.file-extensions}")
    public void setPossibleGameFileExtensions(List<String> possibleGameFileExtensions) {
        FilenameUtil.possibleGameFileExtensions = possibleGameFileExtensions;
    }
    
    @Value("${gameyfin.file-suffixes}")
    public void setPossibleGameFileSuffixes(List<String> possibleGameFileSuffixes) {
        // Sort in descending length, so for example "windows" gets checked before "win"
        possibleGameFileSuffixes.sort((s1,s2) -> Integer.compare(s2.length(), s1.length()));
        FilenameUtil.possibleGameFileSuffixes = possibleGameFileSuffixes;
    }

    public static String getFilenameWithoutExtension(Path p) {

        // If the path points to a folder, return the folder name
        // Folders like "Counter Strike 1.6" would otherwise be returned as "Counter Strike 1"
        if(Files.isDirectory(p)) return FilenameUtils.getName(p.toString());

        return FilenameUtils.getBaseName(p.toString());
    }

    public static String getFilenameWithExtension(Path p) {
        return FilenameUtils.getName(p.toString());
    }

    public static boolean hasGameArchiveExtension(Path p) {
        return possibleGameFileExtensions.contains(FilenameUtils.getExtension(p.getFileName().toString()));
    }
    
    public static String getFilenameWithoutAdditions(Path p) {
        String name = getFilenameWithoutExtension(p).toLowerCase();
        for(String suffix : possibleGameFileSuffixes) {
            name = name.replace(suffix, "");
        }
        name = removePattern(name, versionPattern);
        name = removePattern(name, trailingNoisePattern);
        name = removePattern(name, headingNoisePattern);
        
        // sanity check to never return an empty name
        return name.isBlank() ? getFilenameWithoutExtension(p) : name;
    }
    
    public static String removePattern(String string, Pattern pattern) {
        Matcher matcher = pattern.matcher(string);
        if(matcher.find()) {
            return matcher.replaceAll("");
        }
        return string;
    }

}
