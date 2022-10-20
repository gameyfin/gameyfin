package de.grimsi.gameyfin.util;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class FilenameUtilTest {

    private static final FileSystem unixFS = Jimfs.newFileSystem(Configuration.unix());
    private static final FileSystem osxFS = Jimfs.newFileSystem(Configuration.osX());
    private static final FileSystem winFS = Jimfs.newFileSystem(Configuration.windows());
    private static final List<String> gameFileExtensions = List.of("extension_1", "extension_2", "extension_3");

    @AfterAll
    static void close() throws IOException {
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
    }

    @ParameterizedTest
    @MethodSource("fileSystems")
    void getFilenameWithoutExtension_Folder(FileSystem fileSystem) throws IOException {
        String filename = "example_folder";

        Path p = fileSystem.getPath("%s.%s".formatted(filename, gameFileExtensions.get(0)));
        Files.createDirectory(p);

        String result = FilenameUtil.getFilenameWithoutExtension(p);

        assertThat(result).isEqualTo("%s.%s".formatted(filename, gameFileExtensions.get(0)));
    }

    @Test
    void getFilenameWithExtension_Unix() {
    }

    @Test
    void getFilenameWithExtension_OSX() {
    }

    @Test
    void getFilenameWithExtension_Windows() {
    }

    @Test
    void hasGameArchiveExtension_Unix() {
    }

    @Test
    void hasGameArchiveExtension_OSX() {
    }

    @Test
    void hasGameArchiveExtension_Windows() {
    }

    private static Stream<Arguments> fileSystems() {
        return Stream.of(
                arguments(named("Unix", unixFS)),
                arguments(named("OSX", osxFS)),
                arguments(named("Windows", winFS))
        );
    }
}