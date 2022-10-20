package de.grimsi.gameyfin.util;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
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

    @BeforeAll
    static void init() {
        new FilenameUtil().setPossibleGameFileExtensions(gameFileExtensions);
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

    private static Stream<Arguments> fileSystems() {
        return Stream.of(
                arguments(named("Unix", unixFS)),
                arguments(named("OSX", osxFS)),
                arguments(named("Windows", winFS))
        );
    }
}