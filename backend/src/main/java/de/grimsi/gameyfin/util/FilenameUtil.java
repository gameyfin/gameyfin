package de.grimsi.gameyfin.util;

import de.grimsi.gameyfin.config.properties.GameyfinProperties;
import org.apache.commons.io.FilenameUtils;
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

    // Suppress SONAR detecting this as a potential stack overflow
    // SONAR is correct, but I honestly don't know how to fix it
    // Also it would require 6k+ character long filenames to really overflow the JVM stack
    @SuppressWarnings("java:S5998")
    private static final Pattern trailingNoisePattern = Pattern.compile("( |\\(\\)|\\[]|[-_.])+$");
    @SuppressWarnings("java:S5998")
    private static final Pattern headingNoisePattern = Pattern.compile("^( |\\(\\)|\\[]|[-_.])+");

    public FilenameUtil(GameyfinProperties gameyfinProperties) {
        possibleGameFileExtensions = gameyfinProperties.fileExtensions();

        // Sort in descending length, so for example "windows" gets checked before "win"
        FilenameUtil.possibleGameFileSuffixes = gameyfinProperties.fileSuffixes();
        possibleGameFileSuffixes.sort((s1, s2) -> Integer.compare(s2.length(), s1.length()));
    }

    public static String getFilenameWithoutExtension(Path p) {

        // If the path points to a folder, return the folder name
        // Folders like "Counter Strike 1.6" would otherwise be returned as "Counter Strike 1"
        if (Files.isDirectory(p)) return FilenameUtils.getName(p.toString());

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
        for (String suffix : possibleGameFileSuffixes) {
            name = name.replace(suffix, "");
        }
        name = removePattern(name, versionPattern);
        name = removePattern(name, trailingNoisePattern);
        name = removePattern(name, headingNoisePattern);

        // sanity hasValue to never return an empty name
        return name.isBlank() ? getFilenameWithoutExtension(p) : name;
    }

    public static String removePattern(String string, Pattern pattern) {
        Matcher matcher = pattern.matcher(string);
        if (matcher.find()) {
            return matcher.replaceAll("");
        }
        return string;
    }

}
