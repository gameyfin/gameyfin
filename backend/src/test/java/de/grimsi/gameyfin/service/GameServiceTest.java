package de.grimsi.gameyfin.service;

import com.igdb.proto.Igdb;
import de.grimsi.gameyfin.dto.GameOverviewDto;
import de.grimsi.gameyfin.entities.DetectedGame;
import de.grimsi.gameyfin.entities.Library;
import de.grimsi.gameyfin.entities.Platform;
import de.grimsi.gameyfin.entities.UnmappableFile;
import de.grimsi.gameyfin.igdb.IgdbApiProperties;
import de.grimsi.gameyfin.igdb.IgdbWrapper;
import de.grimsi.gameyfin.mapper.GameMapper;
import de.grimsi.gameyfin.repositories.DetectedGameRepository;
import de.grimsi.gameyfin.repositories.LibraryRepository;
import de.grimsi.gameyfin.repositories.UnmappableFileRepository;
import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.in;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    private final EasyRandom easyRandom = new EasyRandom();

    @InjectMocks
    private GameService target;

    @Mock
    private IgdbWrapper igdbWrapperMock;

    @Mock
    private GameMapper gameMapperMock;

    @Mock
    private DetectedGameRepository detectedGameRepositoryMock;

    @Mock
    private UnmappableFileRepository unmappableFileRepositoryMock;

    @Mock
    private LibraryRepository libraryRepositoryMock;

    @Mock
    private FilesystemService filesystemServiceMock;

    @Test
    void getAllDetectedGames() {
        List<DetectedGame> input = easyRandom.objects(DetectedGame.class, 5).toList();

        when(detectedGameRepositoryMock.findAll()).thenReturn(input);

        List<DetectedGame> result = target.getAllDetectedGames();

        assertThat(result).hasSameElementsAs(input);
        verify(detectedGameRepositoryMock, times(1)).findAll();
    }

    @Test
    void getDetectedGame() {
        String slug = easyRandom.nextObject(String.class);
        DetectedGame input = easyRandom.nextObject(DetectedGame.class);

        when(detectedGameRepositoryMock.findById(slug)).thenReturn(Optional.of(input));

        DetectedGame result = target.getDetectedGame(slug);

        assertThat(result).isEqualTo(input);
        verify(detectedGameRepositoryMock, times(1)).findById(slug);
    }

    @Test
    void getDetectedGame_NotFound() {
        String slug = easyRandom.nextObject(String.class);

        when(detectedGameRepositoryMock.findById(slug)).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> target.getDetectedGame(slug));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(detectedGameRepositoryMock, times(1)).findById(slug);
    }

    @Test
    void getAllUnmappedFiles() {
        List<UnmappableFile> input = easyRandom.objects(UnmappableFile.class, 5).toList();

        when(unmappableFileRepositoryMock.findAll()).thenReturn(input);

        List<UnmappableFile> result = target.getAllUnmappedFiles();

        assertThat(result).hasSameElementsAs(input);
        verify(unmappableFileRepositoryMock, times(1)).findAll();
    }

    @Test
    void getAllMappings() {
        Stream<DetectedGame> gameStream = easyRandom.objects(DetectedGame.class, 5);
        List<DetectedGame> games = gameStream.toList();
        Map<String, String> input = games.stream().collect(Collectors.toMap(DetectedGame::getPath, DetectedGame::getTitle));

        when(detectedGameRepositoryMock.findAll()).thenReturn(games);

        Map<String, String> result = target.getAllMappings();

        assertThat(result).containsAllEntriesOf(input);
        verify(detectedGameRepositoryMock, times(1)).findAll();
    }

    @Test
    void getGameOverviews() {
        Stream<DetectedGame> gameStream = easyRandom.objects(DetectedGame.class, 5);
        List<DetectedGame> games = gameStream.toList();
        List<GameOverviewDto> input = games.stream()
                .map(d -> GameOverviewDto.builder()
                        .coverId(d.getCoverId())
                        .slug(d.getSlug())
                        .title(d.getTitle())
                        .build())
                .toList();

        when(detectedGameRepositoryMock.findAll()).thenReturn(games);
        when(gameMapperMock.toGameOverviewDto(any())).thenCallRealMethod();

        List<GameOverviewDto> result = target.getGameOverviews();

        assertThat(result).hasSameElementsAs(input);
        verify(detectedGameRepositoryMock, times(1)).findAll();
    }

    @Test
    void deleteGame() {
        DetectedGame input = easyRandom.nextObject(DetectedGame.class);

        when(detectedGameRepositoryMock.findById(input.getSlug())).thenReturn(Optional.of(input));

        target.deleteGame(input.getSlug());

        verify(detectedGameRepositoryMock, times(1)).findById(input.getSlug());
        verify(unmappableFileRepositoryMock, times(1)).save(new UnmappableFile(input.getPath()));
        verify(detectedGameRepositoryMock, times(1)).delete(input);
    }

    @Test
    void deleteUnmappedFile() {
        Long input = easyRandom.nextLong();

        target.deleteUnmappedFile(input);

        verify(unmappableFileRepositoryMock, times(1)).deleteById(input);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void confirmGame(boolean confirmMatch) {
        DetectedGame input = easyRandom.nextObject(DetectedGame.class);
        input.setConfirmedMatch(!confirmMatch);

        when(detectedGameRepositoryMock.findById(input.getSlug())).thenReturn(Optional.of(input));
        when(detectedGameRepositoryMock.save(any(DetectedGame.class))).thenAnswer(invocation -> invocation.getArgument(0, DetectedGame.class));

        DetectedGame result = target.confirmGame(input.getSlug(), confirmMatch);

        assertThat(result).usingRecursiveComparison()
                .ignoringFields("confirmedMatch")
                .isEqualTo(input);
        assertThat(result.isConfirmedMatch()).isEqualTo(confirmMatch);

        verify(detectedGameRepositoryMock, times(1)).save(result);
    }

    @Test
    void mapPathToGame_UnmappableFile() {
        DetectedGame mockedDetectedGame = easyRandom.nextObject(DetectedGame.class);
        mockedDetectedGame.setConfirmedMatch(false);
        UnmappableFile input = new UnmappableFile(mockedDetectedGame.getPath());
        String slug = easyRandom.nextObject(String.class);
        Library mockedLibrary = Library.builder()
                .path(input.getPath())
                .platforms(easyRandom.objects(Platform.class, 5).toList())
                .build();

        when(detectedGameRepositoryMock.existsBySlug(slug)).thenReturn(false);
        when(detectedGameRepositoryMock.save(any(DetectedGame.class))).thenAnswer(invocation -> invocation.getArgument(0, DetectedGame.class));
        when(unmappableFileRepositoryMock.findByPath(input.getPath())).thenReturn(Optional.of(input));
        when(filesystemServiceMock.getPath(input.getPath())).thenReturn(Path.of("parent", input.getPath()));
        when(igdbWrapperMock.getGameBySlug(slug)).thenReturn(Optional.of(Igdb.Game.newBuilder().build()));
        when(libraryRepositoryMock.findByPath(any())).thenReturn(Optional.of(mockedLibrary));
        when(gameMapperMock.toDetectedGame(any(Igdb.Game.class), any(Path.class), any(Library.class))).thenReturn(mockedDetectedGame);

        DetectedGame result = target.mapPathToGame(input.getPath(), slug);

        verify(detectedGameRepositoryMock, times(1)).existsBySlug(slug);
        verify(detectedGameRepositoryMock, never()).findByPath(input.getPath());
        verify(unmappableFileRepositoryMock, times(1)).findByPath(input.getPath());

        assertThat(result).usingRecursiveComparison()
                .ignoringFields("confirmedMatch")
                .isEqualTo(mockedDetectedGame);
        assertThat(result.isConfirmedMatch()).isTrue();
    }

    @Test
    void mapPathToGame_DetectedGame() {
        DetectedGame input = easyRandom.nextObject(DetectedGame.class);
        input.setConfirmedMatch(false);
        String slug = easyRandom.nextObject(String.class);
        Library mockedLibrary = Library.builder()
                .path(input.getPath())
                .platforms(easyRandom.objects(Platform.class, 5).toList())
                .build();

        when(detectedGameRepositoryMock.existsBySlug(slug)).thenReturn(false);
        when(detectedGameRepositoryMock.save(any(DetectedGame.class))).thenAnswer(invocation -> invocation.getArgument(0, DetectedGame.class));
        when(detectedGameRepositoryMock.findByPath(input.getPath())).thenReturn(Optional.of(input));
        when(unmappableFileRepositoryMock.findByPath(input.getPath())).thenReturn(Optional.empty());
        when(filesystemServiceMock.getPath(input.getPath())).thenReturn(Path.of("parent", input.getPath()));
        when(igdbWrapperMock.getGameBySlug(slug)).thenReturn(Optional.of(Igdb.Game.newBuilder().build()));
        when(libraryRepositoryMock.findByPath(any())).thenReturn(Optional.of(mockedLibrary));
        when(gameMapperMock.toDetectedGame(any(Igdb.Game.class), any(Path.class), any(Library.class))).thenReturn(input);

        DetectedGame result = target.mapPathToGame(input.getPath(), slug);

        verify(detectedGameRepositoryMock, times(1)).existsBySlug(slug);
        verify(detectedGameRepositoryMock, times(1)).findByPath(input.getPath());
        verify(unmappableFileRepositoryMock, times(1)).findByPath(input.getPath());

        assertThat(result).usingRecursiveComparison()
                .ignoringFields("confirmedMatch")
                .isEqualTo(input);
        assertThat(result.isConfirmedMatch()).isTrue();
    }

    @Test
    void mapPathToGame_SlugAlreadyInDatabase() {
        DetectedGame input = easyRandom.nextObject(DetectedGame.class);

        when(detectedGameRepositoryMock.existsBySlug(input.getSlug())).thenReturn(true);

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> target.mapPathToGame(input.getPath(), input.getSlug()));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.CONFLICT);
        verify(detectedGameRepositoryMock, times(1)).existsBySlug(input.getSlug());
        verify(detectedGameRepositoryMock, never()).findByPath(input.getPath());
        verify(unmappableFileRepositoryMock, never()).findByPath(input.getPath());
    }

    @Test
    void mapPathToGame_PathNotInDatabase() {
        DetectedGame input = easyRandom.nextObject(DetectedGame.class);

        when(detectedGameRepositoryMock.existsBySlug(input.getSlug())).thenReturn(false);
        when(detectedGameRepositoryMock.findByPath(input.getPath())).thenReturn(Optional.empty());
        when(unmappableFileRepositoryMock.findByPath(input.getPath())).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> target.mapPathToGame(input.getPath(), input.getSlug()));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(detectedGameRepositoryMock, times(1)).existsBySlug(input.getSlug());
        verify(detectedGameRepositoryMock, times(1)).findByPath(input.getPath());
        verify(unmappableFileRepositoryMock, times(1)).findByPath(input.getPath());
    }

    @Test
    void refreshGame() {
        DetectedGame input = easyRandom.nextObject(DetectedGame.class);
        input.setConfirmedMatch(true);
        Library mockedLibrary = Library.builder()
                .path(input.getPath())
                .platforms(easyRandom.objects(Platform.class, 5).toList())
                .build();

        when(detectedGameRepositoryMock.findById(input.getSlug())).thenReturn(Optional.of(input));
        when(detectedGameRepositoryMock.save(any(DetectedGame.class))).thenAnswer(invocation -> invocation.getArgument(0, DetectedGame.class));
        when(filesystemServiceMock.getPath(input.getPath())).thenReturn(Path.of("parent", input.getPath()));
        when(igdbWrapperMock.getGameBySlug(input.getSlug())).thenReturn(Optional.of(Igdb.Game.newBuilder().build()));
        when(libraryRepositoryMock.findByPath(any())).thenReturn(Optional.of(mockedLibrary));
        when(gameMapperMock.toDetectedGame(any(Igdb.Game.class), any(Path.class), any(Library.class))).thenReturn(input);

        DetectedGame result = target.refreshGame(input.getSlug());

        assertThat(result).usingRecursiveComparison()
                .ignoringFields("confirmedMatch")
                .isEqualTo(input);
        assertThat(result.isConfirmedMatch()).isTrue();
    }

    @Test
    void refreshGame_NotFound() {
        String slug = easyRandom.nextObject(String.class);

        when(detectedGameRepositoryMock.findById(slug)).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class, () -> target.refreshGame(slug));

        assertThat(e.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(detectedGameRepositoryMock, times(1)).findById(slug);
    }
}
