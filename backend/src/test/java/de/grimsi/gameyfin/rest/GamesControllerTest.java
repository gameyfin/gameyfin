package de.grimsi.gameyfin.rest;

import de.grimsi.gameyfin.dto.GameOverviewDto;
import de.grimsi.gameyfin.entities.DetectedGame;
import de.grimsi.gameyfin.service.DownloadService;
import de.grimsi.gameyfin.service.GameService;
import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.in;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GamesControllerTest {

    @InjectMocks
    private GamesController target;

    @Mock
    private GameService gameServiceMock;

    @Mock
    private DownloadService downloadServiceMock;

    private final EasyRandom easyRandom = new EasyRandom();

    @Test
    void getAllGames() {
        List<DetectedGame> input = easyRandom.objects(DetectedGame.class, 5).toList();

        when(gameServiceMock.getAllDetectedGames()).thenReturn(input);

        List<DetectedGame> result = target.getAllGames();

        verify(gameServiceMock, times(1)).getAllDetectedGames();
        assertThat(result).hasSameElementsAs(input);
    }

    @Test
    void getGame() {
        DetectedGame input = easyRandom.nextObject(DetectedGame.class);
        String slug = input.getSlug();

        when(gameServiceMock.getDetectedGame(slug)).thenReturn(input);

        DetectedGame result = target.getGame(slug);

        verify(gameServiceMock, times(1)).getDetectedGame(slug);
        assertThat(result).isEqualTo(input);
    }

    @Test
    void getGameOverviews() {
        List<GameOverviewDto> input = easyRandom.objects(GameOverviewDto.class, 5).toList();

        when(gameServiceMock.getGameOverviews()).thenReturn(input);

        List<GameOverviewDto> result = target.getGameOverviews();

        verify(gameServiceMock, times(1)).getGameOverviews();
        assertThat(result).hasSameElementsAs(input);
    }

    @Test
    void getGameMappings() {
        Map<String, String> input = easyRandom.objects(String.class, 5).collect(Collectors.toMap(String::toLowerCase, String::toUpperCase));

        when(gameServiceMock.getAllMappings()).thenReturn(input);

        Map<String, String> result = target.getGameMappings();

        verify(gameServiceMock, times(1)).getAllMappings();
        assertThat(result).isEqualTo(input);
    }

    @Test
    void downloadGameFiles_File() {
        DetectedGame input = easyRandom.nextObject(DetectedGame.class);
        String slug = input.getSlug();
        String downloadFilename = input.getSlug();
        long downloadFileSize = 1337L;

        when(gameServiceMock.getDetectedGame(slug)).thenReturn(input);
        when(downloadServiceMock.getDownloadFileName(input)).thenReturn(downloadFilename);
        when(downloadServiceMock.getDownloadFileSize(input)).thenReturn(downloadFileSize);

        ResponseEntity<StreamingResponseBody> result = target.downloadGameFiles(slug);

        HttpHeaders expectedHeaders = new HttpHeaders();
        expectedHeaders.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"%s\"".formatted(downloadFilename));
        expectedHeaders.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
        expectedHeaders.add(HttpHeaders.PRAGMA, "no-cache");
        expectedHeaders.add(HttpHeaders.EXPIRES, "0");
        expectedHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        expectedHeaders.setContentLength(downloadFileSize);

        verify(gameServiceMock, times(1)).getDetectedGame(slug);
        verify(downloadServiceMock, times(1)).getDownloadFileName(input);
        verify(downloadServiceMock, times(1)).getDownloadFileSize(input);
        assertThat(result.getHeaders()).isEqualTo(expectedHeaders);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void downloadGameFiles_Zip() {
        DetectedGame input = easyRandom.nextObject(DetectedGame.class);
        String slug = input.getSlug();
        String downloadFilename = input.getSlug() + ".zip";
        long downloadFileSize = 0L;

        when(gameServiceMock.getDetectedGame(slug)).thenReturn(input);
        when(downloadServiceMock.getDownloadFileName(input)).thenReturn(downloadFilename);
        when(downloadServiceMock.getDownloadFileSize(input)).thenReturn(downloadFileSize);

        ResponseEntity<StreamingResponseBody> result = target.downloadGameFiles(slug);

        HttpHeaders expectedHeaders = new HttpHeaders();
        expectedHeaders.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"%s\"".formatted(downloadFilename));
        expectedHeaders.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
        expectedHeaders.add(HttpHeaders.PRAGMA, "no-cache");
        expectedHeaders.add(HttpHeaders.EXPIRES, "0");
        expectedHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);

        verify(gameServiceMock, times(1)).getDetectedGame(slug);
        verify(downloadServiceMock, times(1)).getDownloadFileName(input);
        verify(downloadServiceMock, times(1)).getDownloadFileSize(input);
        assertThat(result.getHeaders()).isEqualTo(expectedHeaders);
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void refreshGame() {
        DetectedGame input = easyRandom.nextObject(DetectedGame.class);
        String slug = input.getSlug();

        when(gameServiceMock.refreshGame(slug)).thenReturn(input);

        DetectedGame result = target.refreshGame(slug);

        verify(gameServiceMock, times(1)).refreshGame(slug);
        assertThat(result).isEqualTo(input);
    }
}