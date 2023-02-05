package de.grimsi.gameyfin.service;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.*;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

@ExtendWith(MockitoExtension.class)
class FilesystemServiceTest {

    @InjectMocks
    private FilesystemService target;

    private final EasyRandom easyRandom = new EasyRandom();

    private static final FileSystem unixFS = Jimfs.newFileSystem(Configuration.unix());
    private static final FileSystem osxFS = Jimfs.newFileSystem(Configuration.osX());
    private static final FileSystem winFS = Jimfs.newFileSystem(Configuration.windows());

    private static final String CACHE_PATH = "path/to/cache";

    void setup(FileSystem fileSystem) {
        ReflectionTestUtils.setField(target, "fileSystem", fileSystem);
        ReflectionTestUtils.setField(target, "cacheFolderPath", CACHE_PATH);
        target.createCacheFolder();
    }

    @AfterAll
    static void closeFileSystems() throws IOException {
        unixFS.close();
        osxFS.close();
        winFS.close();
    }

    @ParameterizedTest
    @MethodSource("fileSystems")
    void getPath(FileSystem fileSystem) throws IOException {
        setup(fileSystem);

        String testPath = "some/random/path";
        Path input = fileSystem.getPath(testPath);
        Files.createDirectories(input);

        Path result = target.getPath(testPath);

        assertThat(result).isEqualTo(input);

        Files.deleteIfExists(input);
    }

    @ParameterizedTest
    @MethodSource("fileSystems")
    void createCacheFolder(FileSystem fileSystem) throws IOException {
        setup(fileSystem);

        Path cache = fileSystem.getPath(CACHE_PATH);
        Files.deleteIfExists(cache);

        assertThat(Files.exists(cache)).isFalse();

        target.createCacheFolder();

        assertThat(Files.exists(cache)).isTrue();
    }

    @ParameterizedTest
    @MethodSource("fileSystems")
    void saveFileToCache(FileSystem fileSystem) throws IOException {
        setup(fileSystem);

        String fileName = easyRandom.nextObject(String.class);
        byte[] fileContent = new byte[1024];
        easyRandom.nextBytes(fileContent);
        Path savedFilePath = fileSystem.getPath(CACHE_PATH, fileName);

        try(InputStream i = new ByteArrayInputStream(fileContent)) {
            DataBufferFactory dbFactory = new DefaultDataBufferFactory();
            Flux<DataBuffer> d = DataBufferUtils.readInputStream(() -> i, dbFactory, 1);

            target.saveFileToCache(d, fileName);

            assertThat(Files.readAllBytes(savedFilePath)).isEqualTo(fileContent);
        }

        Files.deleteIfExists(savedFilePath);
    }

    @ParameterizedTest
    @MethodSource("fileSystems")
    void getFileFromCache(FileSystem fileSystem) throws IOException {
        setup(fileSystem);

        String fileName = easyRandom.nextObject(String.class);
        byte[] fileContent = new byte[1024];
        easyRandom.nextBytes(fileContent);
        Path savedFilePath = fileSystem.getPath(CACHE_PATH, fileName);

        Files.write(savedFilePath, fileContent);

        ByteArrayResource expected = new ByteArrayResource(fileContent);

        assertThat(target.getFileFromCache(fileName)).isEqualTo(expected);

        Files.deleteIfExists(savedFilePath);
    }

    @ParameterizedTest
    @MethodSource("fileSystems")
    void deleteFileFromCache(FileSystem fileSystem) throws IOException {
        setup(fileSystem);

        String fileName = easyRandom.nextObject(String.class);
        byte[] fileContent = new byte[1024];
        easyRandom.nextBytes(fileContent);
        Path savedFilePath = fileSystem.getPath(CACHE_PATH, fileName);

        Files.write(savedFilePath, fileContent);
        assertThat(Files.exists(savedFilePath)).isTrue();

        target.deleteFileFromCache(fileName);

        assertThat(Files.exists(savedFilePath)).isFalse();

        Files.deleteIfExists(savedFilePath);
    }

    @ParameterizedTest
    @MethodSource("fileSystems")
    void isCachedFileCorrupt_True(FileSystem fileSystem) throws IOException {
        setup(fileSystem);

        String fileName = easyRandom.nextObject(String.class);
        Path savedFilePath = fileSystem.getPath(CACHE_PATH, fileName);

        Files.write(savedFilePath, new byte[0]);
        assertThat(target.isCachedFileCorrupt(fileName)).isTrue();

        Files.deleteIfExists(savedFilePath);
    }

    @ParameterizedTest
    @MethodSource("fileSystems")
    void isCachedFileCorrupt_False(FileSystem fileSystem) throws IOException {
        setup(fileSystem);

        String fileName = easyRandom.nextObject(String.class);
        byte[] fileContent = new byte[1024];
        easyRandom.nextBytes(fileContent);
        Path savedFilePath = fileSystem.getPath(CACHE_PATH, fileName);

        Files.write(savedFilePath, fileContent);
        assertThat(target.isCachedFileCorrupt(fileName)).isFalse();

        Files.deleteIfExists(savedFilePath);
    }

    @ParameterizedTest
    @MethodSource("fileSystems")
    void doesCachedFileExist(FileSystem fileSystem) throws IOException {
        setup(fileSystem);

        String fileName = easyRandom.nextObject(String.class);
        byte[] fileContent = new byte[1024];
        easyRandom.nextBytes(fileContent);
        Path savedFilePath = fileSystem.getPath(CACHE_PATH, fileName);

        assertThat(target.doesCachedFileExist(fileName)).isFalse();

        Files.write(savedFilePath, fileContent);

        assertThat(target.doesCachedFileExist(fileName)).isTrue();

        Files.deleteIfExists(savedFilePath);
    }

    @Disabled("Due to JimFS not supporting the \"Path.toFile()\" call")
    @ParameterizedTest
    @MethodSource("fileSystems")
    void getSizeOnDisk_Directory(FileSystem fileSystem) throws IOException {
        setup(fileSystem);

        String directoryName = easyRandom.nextObject(String.class);
        int fileSize = 1024;
        int fileCount = 5;

        Files.createDirectories(fileSystem.getPath(directoryName));

        for(int i = 0; i < fileCount; i++) {
            String fileName = easyRandom.nextObject(String.class);
            byte[] fileContent = new byte[fileSize];
            easyRandom.nextBytes(fileContent);
            Files.write(fileSystem.getPath(directoryName, fileName), fileContent);
        }

        long directorySize = target.getSizeOnDisk(fileSystem.getPath(directoryName));

        assertThat(directorySize).isEqualTo(fileSize * fileCount);
    }

    @Disabled("Due to JimFS not supporting the \"Path.toFile()\" call")
    @ParameterizedTest
    @MethodSource("fileSystems")
    void getSizeOnDisk_File(FileSystem fileSystem) throws IOException {
        setup(fileSystem);

        String directoryName = easyRandom.nextObject(String.class);
        int fileSize = 1024;
        String fileName = easyRandom.nextObject(String.class);
        byte[] fileContent = new byte[fileSize];
        easyRandom.nextBytes(fileContent);

        Files.write(fileSystem.getPath(directoryName, fileName), fileContent);

        long directorySize = target.getSizeOnDisk(fileSystem.getPath(directoryName));

        assertThat(directorySize).isEqualTo(fileSize);
    }

    private static Stream<Arguments> fileSystems() {
        return Stream.of(
                arguments(named("Unix", unixFS)),
                arguments(named("OSX", osxFS)),
                arguments(named("Windows", winFS))
        );
    }
}
