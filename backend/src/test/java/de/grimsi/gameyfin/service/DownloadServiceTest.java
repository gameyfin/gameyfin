package de.grimsi.gameyfin.service;

import de.grimsi.gameyfin.entities.DetectedGame;
import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DownloadServiceTest {

    @Mock
    private FilesystemService filesystemServiceMock;

    @InjectMocks
    private DownloadService target;

    private final EasyRandom easyRandom = new EasyRandom();

    @Test
    void getDownloadFileName_File() {
        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            DetectedGame input = easyRandom.nextObject(DetectedGame.class);

            filesMock.when(() -> Files.isDirectory(any())).thenReturn(false);
            when(filesystemServiceMock.getPath(any())).thenReturn(Path.of(input.getPath()));

            String result = target.getDownloadFileName(input);

            assertThat(result).isEqualTo(input.getPath());
        }
    }

    @Test
    void getDownloadFileName_Folder() {
        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.isDirectory(any())).thenReturn(true);

            DetectedGame input = easyRandom.nextObject(DetectedGame.class);
            when(filesystemServiceMock.getPath(any())).thenReturn(Path.of(input.getPath()));

            String result = target.getDownloadFileName(input);

            assertThat(result).isEqualTo("%s.zip".formatted(input.getPath()));
        }
    }

    @Test
    void getDownloadFileSize_File() throws IOException {
        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.isDirectory(any())).thenReturn(false);
            when(filesystemServiceMock.getSizeOnDisk(any())).thenReturn(1337L);

            DetectedGame input = easyRandom.nextObject(DetectedGame.class);

            Long result = target.getDownloadFileSize(input);

            assertThat(result).isEqualTo(1337L);
        }
    }

    @Test
    void getDownloadFileSize_Folder() {
        try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
            filesMock.when(() -> Files.isDirectory(any())).thenReturn(true);

            DetectedGame input = easyRandom.nextObject(DetectedGame.class);

            Long result = target.getDownloadFileSize(input);

            assertThat(result).isEqualTo(0L);
        }
    }
}
