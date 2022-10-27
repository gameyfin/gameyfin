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

import java.util.List;
import java.util.Map;
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

        verify(gameServiceMock, times(1)).getDetectedGame(eq(slug));
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
    void downloadGameFiles() {
    }

    @Test
    void refreshGame() {
    }
}