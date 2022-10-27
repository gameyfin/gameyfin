package de.grimsi.gameyfin.rest;

import de.grimsi.gameyfin.dto.AutocompleteSuggestionDto;
import de.grimsi.gameyfin.dto.PathToSlugDto;
import de.grimsi.gameyfin.entities.DetectedGame;
import de.grimsi.gameyfin.entities.Library;
import de.grimsi.gameyfin.entities.Platform;
import de.grimsi.gameyfin.service.GameService;
import de.grimsi.gameyfin.service.ImageService;
import de.grimsi.gameyfin.service.LibraryService;
import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LibraryManagementControllerTest {

    @InjectMocks
    private LibraryManagementController target;

    @Mock
    private GameService gameServiceMock;

    @Mock
    private ImageService imageServiceMock;

    @Mock
    private LibraryService libraryServiceMock;

    private final EasyRandom easyRandom = new EasyRandom();

    @Test
    void deleteGame() {
        String slug = easyRandom.nextObject(String.class);

        target.deleteGame(slug);

        verify(gameServiceMock, times(1)).deleteGame(slug);
    }

    @Test
    void deleteUnmappedFile() {
        Long id = easyRandom.nextLong();

        target.deleteUnmappedFile(id);

        verify(gameServiceMock, times(1)).deleteUnmappedFile(id);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void confirmMatch(boolean confirm) {
        String slug = easyRandom.nextObject(String.class);

        target.confirmMatch(slug, confirm);

        verify(gameServiceMock, times(1)).confirmGame(slug, confirm);
    }

    @Test
    void manuallyMapPathToSlug() {
        PathToSlugDto input = easyRandom.nextObject(PathToSlugDto.class);
        DetectedGame game = easyRandom.nextObject(DetectedGame.class);

        when(gameServiceMock.mapPathToGame(input.getPath(), input.getSlug())).thenReturn(game);

        DetectedGame result = target.manuallyMapPathToSlug(input);

        verify(gameServiceMock, times(1)).mapPathToGame(input.getPath(), input.getSlug());
        verify(imageServiceMock, times(1)).downloadGameScreenshotsFromIgdb();
        verify(imageServiceMock, times(1)).downloadGameCoversFromIgdb();
        verify(imageServiceMock, times(1)).downloadCompanyLogosFromIgdb();

        assertThat(result).isEqualTo(game);
    }

    @Test
    void getUnmappedFiles() {
        target.getUnmappedFiles();

        verify(gameServiceMock, times(1)).getAllUnmappedFiles();
    }

    @Test
    void getAutocompleteSuggestions() {
        String searchTerm = easyRandom.nextObject(String.class);
        int limit = 10;
        List<AutocompleteSuggestionDto> a = easyRandom.objects(AutocompleteSuggestionDto.class, limit).toList();

        when(libraryServiceMock.getAutocompleteSuggestions(searchTerm, limit)).thenReturn(a);

        List<AutocompleteSuggestionDto> result = target.getAutocompleteSuggestions(searchTerm, limit);

        verify(libraryServiceMock, times(1)).getAutocompleteSuggestions(searchTerm, limit);
        assertThat(result).isEqualTo(a);
    }

    @Test
    void getPlatforms() {
        String searchTerm = easyRandom.nextObject(String.class);
        int limit = 10;
        List<Platform> p = easyRandom.objects(Platform.class, limit).toList();

        when(libraryServiceMock.getPlatforms(searchTerm, limit)).thenReturn(p);

        List<Platform> result = target.getPlatforms(searchTerm, limit);

        verify(libraryServiceMock, times(1)).getPlatforms(searchTerm, limit);
        assertThat(result).isEqualTo(p);
    }

    @Test
    void getLibraries() {
        target.getLibraries();

        verify(libraryServiceMock, times(1)).getOrCreateLibraries();
    }

    @Test
    void mapPathToPlatform() {
        PathToSlugDto input = easyRandom.nextObject(PathToSlugDto.class);
        Library l = easyRandom.nextObject(Library.class);

        when(libraryServiceMock.mapPlatformsToLibrary(input.getPath(), input.getSlug())).thenReturn(l);

        Library result = target.mapPathToPlatform(input);

        verify(libraryServiceMock, times(1)).mapPlatformsToLibrary(input.getPath(), input.getSlug());
        assertThat(result).isEqualTo(l);
    }
}
