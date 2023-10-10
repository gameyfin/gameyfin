package de.grimsi.gameyfin.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import de.grimsi.gameyfin.config.properties.GameyfinProperties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;

class FilenameUtilTest {

    private static final GameyfinProperties gameyfinPropertiesMock = mock(GameyfinProperties.class);
    private static final FileSystem unixFS = Jimfs.newFileSystem(Configuration.unix());
    private static final FileSystem osxFS = Jimfs.newFileSystem(Configuration.osX());
    private static final FileSystem winFS = Jimfs.newFileSystem(Configuration.windows());

    private static final List<String> gameFileExtensions = List.of("extension_1", "extension_2", "extension_3");
    private static final List<String> possibleGameFileSuffixes = Arrays.asList("windows, win, english, win32, win64, opengl, stable".split(", "));

    @BeforeAll
    static void init() {
        when(gameyfinPropertiesMock.fileExtensions()).thenReturn(gameFileExtensions);
        when(gameyfinPropertiesMock.fileSuffixes()).thenReturn(possibleGameFileSuffixes);

        FilenameUtil filenameUtil = new FilenameUtil(gameyfinPropertiesMock);
    }

    @AfterAll
    static void closeFileSystems() throws IOException {
        unixFS.close();
        osxFS.close();
        winFS.close();
    }

    @ParameterizedTest
    @MethodSource("fileSystems")
    void getFilenameWithoutExtension_File(FileSystem fileSystem) throws IOException {
        String filename = "example_file";

        Path p = fileSystem.getPath("%s.%s".formatted(filename, gameFileExtensions.get(0)));
        Files.createFile(p);

        String result = FilenameUtil.getFilenameWithoutExtension(p);

        assertThat(result).isEqualTo(filename);

        Files.deleteIfExists(p);
    }

    @ParameterizedTest
    @MethodSource("fileSystems")
    void getFilenameWithoutExtension_Folder(FileSystem fileSystem) throws IOException {
        String filename = "example_folder";

        Path p = fileSystem.getPath("%s.%s".formatted(filename, gameFileExtensions.get(0)));
        Files.createDirectory(p);

        String result = FilenameUtil.getFilenameWithoutExtension(p);

        assertThat(result).isEqualTo("%s.%s".formatted(filename, gameFileExtensions.get(0)));

        Files.deleteIfExists(p);
    }

    @ParameterizedTest
    @MethodSource("fileSystems")
    void getFilenameWithExtension(FileSystem fileSystem) throws IOException {
        String filename = "example_file";

        Path p = fileSystem.getPath("%s.%s".formatted(filename, gameFileExtensions.get(0)));
        Files.createFile(p);

        String result = FilenameUtil.getFilenameWithExtension(p);

        assertThat(result).isEqualTo("%s.%s".formatted(filename, gameFileExtensions.get(0)));

        Files.deleteIfExists(p);
    }

    @ParameterizedTest
    @MethodSource("fileSystems")
    void hasGameArchiveExtension_gameArchive(FileSystem fileSystem) throws IOException {
        String filename = "example_file";

        Path p = fileSystem.getPath("%s.%s".formatted(filename, gameFileExtensions.get(0)));
        Files.createFile(p);

        assertThat(FilenameUtil.hasGameArchiveExtension(p)).isTrue();

        Files.deleteIfExists(p);
    }

    @ParameterizedTest
    @MethodSource("fileSystems")
    void hasGameArchiveExtension_notGameArchive(FileSystem fileSystem) throws IOException {
        String filename = "example_file";

        Path p = fileSystem.getPath("%s.%s".formatted(filename, "some_other_extension"));
        Files.createFile(p);

        assertThat(FilenameUtil.hasGameArchiveExtension(p)).isFalse();

        Files.deleteIfExists(p);
    }
    
    @ParameterizedTest
    @MethodSource("exampleFilenames")
    void removeFileSuffixes(String filename) {
        assertEquals(filename, FilenameUtil.getFilenameWithoutAdditions(unixFS.getPath("%s.%s".formatted(filename, "-win"))));
        assertEquals(filename, FilenameUtil.getFilenameWithoutAdditions(unixFS.getPath("%s.%s".formatted(filename, "-v1.05.4"))));
        assertEquals(filename, FilenameUtil.getFilenameWithoutAdditions(unixFS.getPath("%s.%s".formatted(filename, "-win32"))));
        assertEquals(filename, FilenameUtil.getFilenameWithoutAdditions(unixFS.getPath("%s.%s".formatted(filename, "-win-opengl(windows)"))));
        assertEquals(filename, FilenameUtil.getFilenameWithoutAdditions(unixFS.getPath("%s.%s".formatted(filename, "-windows-stable"))));
        assertEquals(filename, FilenameUtil.getFilenameWithoutAdditions(unixFS.getPath("%s.%s".formatted(filename, "[windows]"))));
        assertEquals(filename, FilenameUtil.getFilenameWithoutAdditions(unixFS.getPath("%s.%s".formatted(filename, "[stable]"))));
        assertEquals(filename, FilenameUtil.getFilenameWithoutAdditions(unixFS.getPath("%s.%s".formatted(filename, "(opengl)"))));
    }
    
    @ParameterizedTest
    @MethodSource("exampleFilenames")
    void removeFileSuffixesFileExtensions(String filename) {
        assertEquals(filename, FilenameUtil.getFilenameWithoutAdditions(unixFS.getPath("%s.%s.%s".formatted(filename, "-win", gameFileExtensions.get(0)))));
        assertEquals(filename, FilenameUtil.getFilenameWithoutAdditions(unixFS.getPath("%s.%s.%s".formatted(filename, "-v1.05.4", gameFileExtensions.get(0)))));
        assertEquals(filename, FilenameUtil.getFilenameWithoutAdditions(unixFS.getPath("%s.%s.%s".formatted(filename, "-win32", gameFileExtensions.get(0)))));
        assertEquals(filename, FilenameUtil.getFilenameWithoutAdditions(unixFS.getPath("%s.%s.%s".formatted(filename, "-win-opengl(windows)", gameFileExtensions.get(0)))));
        assertEquals(filename, FilenameUtil.getFilenameWithoutAdditions(unixFS.getPath("%s.%s.%s".formatted(filename, "-windows-stable", gameFileExtensions.get(0)))));
        assertEquals(filename, FilenameUtil.getFilenameWithoutAdditions(unixFS.getPath("%s.%s.%s".formatted(filename, "[windows]", gameFileExtensions.get(0)))));
        assertEquals(filename, FilenameUtil.getFilenameWithoutAdditions(unixFS.getPath("%s.%s.%s".formatted(filename, "[stable]", gameFileExtensions.get(0)))));
        assertEquals(filename, FilenameUtil.getFilenameWithoutAdditions(unixFS.getPath("%s.%s.%s".formatted(filename, "(opengl)", gameFileExtensions.get(0)))));
    }
    
    @ParameterizedTest
    @MethodSource("exampleFilenames")
    void removeFileSuffixesWithAddedSpaces(String filename) {
        assertEquals(filename, FilenameUtil.getFilenameWithoutAdditions(unixFS.getPath("  %s.%s   .%s".formatted(filename, "-win", gameFileExtensions.get(0)))));
        assertEquals(filename, FilenameUtil.getFilenameWithoutAdditions(unixFS.getPath("  %s.%s     .%s".formatted(filename, "-v1.05.4", gameFileExtensions.get(0)))));
        assertEquals(filename, FilenameUtil.getFilenameWithoutAdditions(unixFS.getPath(" %s.%s .%s".formatted(filename, "-win32", gameFileExtensions.get(0)))));
        assertEquals(filename, FilenameUtil.getFilenameWithoutAdditions(unixFS.getPath(" %s.%s   .%s".formatted(filename, "-win-opengl(windows)", gameFileExtensions.get(0)))));
        assertEquals(filename, FilenameUtil.getFilenameWithoutAdditions(unixFS.getPath(" %s.%s  .%s".formatted(filename, "-windows-stable", gameFileExtensions.get(0)))));
        assertEquals(filename, FilenameUtil.getFilenameWithoutAdditions(unixFS.getPath(" %s.%s  .%s".formatted(filename, "[windows]", gameFileExtensions.get(0)))));
        assertEquals(filename, FilenameUtil.getFilenameWithoutAdditions(unixFS.getPath(" %s.%s  .%s".formatted(filename, "[stable]", gameFileExtensions.get(0)))));
        assertEquals(filename, FilenameUtil.getFilenameWithoutAdditions(unixFS.getPath(" %s.%s  .%s".formatted(filename, "(opengl)", gameFileExtensions.get(0)))));
    }


    private static Stream<Arguments> fileSystems() {
        return Stream.of(
                arguments(named("Unix", unixFS)),
                arguments(named("OSX", osxFS)),
                arguments(named("Windows", winFS))
        );
    }
    
    private static Stream<Arguments> exampleFilenames() {
        return Stream.of(
                arguments(named("example_file", "example_file")),
                arguments(named("example-file", "example-file")),
                arguments(named("example file", "example file"))
        );
    }
}