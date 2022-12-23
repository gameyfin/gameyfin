package de.grimsi.gameyfin.service;

import de.grimsi.gameyfin.entities.Company;
import de.grimsi.gameyfin.entities.DetectedGame;
import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageServiceTest {

    private final EasyRandom easyRandom = new EasyRandom();

    private FilesystemService filesystemServiceMock;
    private GameService gameServiceMock;

    private ImageService target;

    @BeforeEach
    void beforeEach() {
        WebClient.Builder webClientBuilderMock = mock(WebClient.Builder.class);
        gameServiceMock = mock(GameService.class);
        filesystemServiceMock = mock(FilesystemService.class);

        target = new ImageService(filesystemServiceMock, gameServiceMock, webClientBuilderMock);

        ReflectionTestUtils.setField(target, "webclientBuilder", webClientBuilderMock);
        when(webClientBuilderMock.baseUrl(any(String.class))).thenReturn(WebClient.builder());

        target.init();
    }

    @Test
    void downloadGameCoversFromIgdb() {
        List<DetectedGame> detectedGames = easyRandom.objects(DetectedGame.class, 5).toList();

        when(gameServiceMock.getAllDetectedGames()).thenReturn(detectedGames);

        target.downloadGameCoversFromIgdb();

        verify(gameServiceMock, times(1)).getAllDetectedGames();
        verify(filesystemServiceMock, times(detectedGames.size())).saveFileToCache(any(), any());
    }

    @Test
    void downloadGameScreenshotsFromIgdb() {
        List<DetectedGame> detectedGames = easyRandom.objects(DetectedGame.class, 5).toList();
        List<String> screenshotIds = detectedGames.stream().flatMap(d -> d.getScreenshotIds().stream()).toList();

        when(gameServiceMock.getAllDetectedGames()).thenReturn(detectedGames);

        target.downloadGameScreenshotsFromIgdb();

        verify(gameServiceMock, times(1)).getAllDetectedGames();
        verify(filesystemServiceMock, times(screenshotIds.size())).saveFileToCache(any(), any());
    }

    @Test
    void downloadCompanyLogosFromIgdb() {
        List<DetectedGame> detectedGames = easyRandom.objects(DetectedGame.class, 5).toList();
        Set<String> companyLogoIds = detectedGames.stream().flatMap(d -> d.getCompanies().stream())
                .map(Company::getLogoId).collect(Collectors.toUnmodifiableSet());

        when(gameServiceMock.getAllDetectedGames()).thenReturn(detectedGames);

        target.downloadCompanyLogosFromIgdb();

        verify(gameServiceMock, times(1)).getAllDetectedGames();
        verify(filesystemServiceMock, times(companyLogoIds.size())).saveFileToCache(any(), any());
    }
}
