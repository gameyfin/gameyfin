package de.grimsi.gameyfin.rest;

import de.grimsi.gameyfin.dto.ImageDownloadResultDto;
import de.grimsi.gameyfin.dto.LibraryScanRequestDto;
import de.grimsi.gameyfin.dto.LibraryScanResult;
import de.grimsi.gameyfin.dto.LibraryScanResultDto;
import de.grimsi.gameyfin.entities.Library;
import de.grimsi.gameyfin.service.ImageService;
import de.grimsi.gameyfin.service.LibraryService;
import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LibraryControllerTest {

    @InjectMocks
    private LibraryController target;

    @Mock
    private LibraryService libraryServiceMock;

    @Mock
    private ImageService imageServiceMock;

    private final EasyRandom easyRandom = new EasyRandom();

    @Test
    void scanLibraries_All_NoImages() {
        int libraryCount = 5;
        LibraryScanRequestDto input = new LibraryScanRequestDto("", false);
        List<Library> libraries = easyRandom.objects(Library.class, libraryCount).toList();
        LibraryScanResult lsr = easyRandom.nextObject(LibraryScanResult.class);

        when(libraryServiceMock.getLibraries()).thenReturn(libraries);
        when(libraryServiceMock.scanGameLibrary(any(Library.class))).thenReturn(lsr);

        LibraryScanResultDto result = target.scanLibraries(input);

        verify(libraryServiceMock, times(1)).getLibraries();
        verify(libraryServiceMock, never()).getLibrary(any(String.class));
        verify(libraryServiceMock, times(libraryCount)).scanGameLibrary(any(Library.class));
        verify(imageServiceMock, never()).downloadCompanyLogosFromIgdb();
        verify(imageServiceMock, never()).downloadGameCoversFromIgdb();
        verify(imageServiceMock, never()).downloadGameScreenshotsFromIgdb();
        assertThat(result.getNewGames()).isEqualTo(lsr.getNewGames() * libraries.size());
        assertThat(result.getDeletedGames()).isEqualTo(lsr.getDeletedGames() * libraries.size());
        assertThat(result.getNewUnmappableFiles()).isEqualTo(lsr.getNewUnmappableFiles() * libraries.size());
        assertThat(result.getTotalGames()).isEqualTo(lsr.getTotalGames() * libraries.size());
        assertThat(result.getCoverDownloads()).isZero();
        assertThat(result.getScreenshotDownloads()).isZero();
        assertThat(result.getCompanyLogoDownloads()).isZero();
    }

    @Test
    void scanLibraries_Single_NoImages() {
        String libraryPath = "some/random/path";
        LibraryScanRequestDto input = new LibraryScanRequestDto(libraryPath, false);
        Library library = easyRandom.nextObject(Library.class);
        LibraryScanResult lsr = easyRandom.nextObject(LibraryScanResult.class);

        when(libraryServiceMock.getLibrary(libraryPath)).thenReturn(library);
        when(libraryServiceMock.scanGameLibrary(any(Library.class))).thenReturn(lsr);

        LibraryScanResultDto result = target.scanLibraries(input);

        verify(libraryServiceMock, never()).getLibraries();
        verify(libraryServiceMock, times(1)).getLibrary(libraryPath);
        verify(libraryServiceMock, times(1)).scanGameLibrary(any(Library.class));
        verify(imageServiceMock, never()).downloadCompanyLogosFromIgdb();
        verify(imageServiceMock, never()).downloadGameCoversFromIgdb();
        verify(imageServiceMock, never()).downloadGameScreenshotsFromIgdb();
        assertThat(result.getNewGames()).isEqualTo(lsr.getNewGames());
        assertThat(result.getDeletedGames()).isEqualTo(lsr.getDeletedGames());
        assertThat(result.getNewUnmappableFiles()).isEqualTo(lsr.getNewUnmappableFiles());
        assertThat(result.getTotalGames()).isEqualTo(lsr.getTotalGames());
        assertThat(result.getCoverDownloads()).isZero();
        assertThat(result.getScreenshotDownloads()).isZero();
        assertThat(result.getCompanyLogoDownloads()).isZero();
    }

    @Test
    void scanLibraries_All_DownloadImages() {
        int libraryCount = 5;
        LibraryScanRequestDto input = new LibraryScanRequestDto("", true);
        List<Library> libraries = easyRandom.objects(Library.class, libraryCount).toList();
        LibraryScanResult lsr = easyRandom.nextObject(LibraryScanResult.class);

        when(libraryServiceMock.getLibraries()).thenReturn(libraries);
        when(libraryServiceMock.scanGameLibrary(any(Library.class))).thenReturn(lsr);
        when(imageServiceMock.downloadGameCoversFromIgdb()).thenReturn(1);
        when(imageServiceMock.downloadGameScreenshotsFromIgdb()).thenReturn(1);
        when(imageServiceMock.downloadCompanyLogosFromIgdb()).thenReturn(1);

        LibraryScanResultDto result = target.scanLibraries(input);

        verify(libraryServiceMock, times(1)).getLibraries();
        verify(libraryServiceMock, never()).getLibrary(any(String.class));
        verify(libraryServiceMock, times(libraryCount)).scanGameLibrary(any(Library.class));
        verify(imageServiceMock, times(1)).downloadCompanyLogosFromIgdb();
        verify(imageServiceMock, times(1)).downloadGameCoversFromIgdb();
        verify(imageServiceMock, times(1)).downloadGameScreenshotsFromIgdb();
        assertThat(result.getNewGames()).isEqualTo(lsr.getNewGames() * libraries.size());
        assertThat(result.getDeletedGames()).isEqualTo(lsr.getDeletedGames() * libraries.size());
        assertThat(result.getNewUnmappableFiles()).isEqualTo(lsr.getNewUnmappableFiles() * libraries.size());
        assertThat(result.getTotalGames()).isEqualTo(lsr.getTotalGames() * libraries.size());
        assertThat(result.getCoverDownloads()).isEqualTo(1);
        assertThat(result.getScreenshotDownloads()).isEqualTo(1);
        assertThat(result.getCompanyLogoDownloads()).isEqualTo(1);
    }

    @Test
    void scanLibraries_Single_DownloadImages() {
        String libraryPath = "some/random/path";
        LibraryScanRequestDto input = new LibraryScanRequestDto(libraryPath, true);
        Library library = easyRandom.nextObject(Library.class);
        LibraryScanResult lsr = easyRandom.nextObject(LibraryScanResult.class);

        when(libraryServiceMock.getLibrary(libraryPath)).thenReturn(library);
        when(libraryServiceMock.scanGameLibrary(any(Library.class))).thenReturn(lsr);
        when(imageServiceMock.downloadGameCoversFromIgdb()).thenReturn(1);
        when(imageServiceMock.downloadGameScreenshotsFromIgdb()).thenReturn(1);
        when(imageServiceMock.downloadCompanyLogosFromIgdb()).thenReturn(1);

        LibraryScanResultDto result = target.scanLibraries(input);

        verify(libraryServiceMock, never()).getLibraries();
        verify(libraryServiceMock, times(1)).getLibrary(libraryPath);
        verify(libraryServiceMock, times(1)).scanGameLibrary(any(Library.class));
        verify(imageServiceMock, times(1)).downloadCompanyLogosFromIgdb();
        verify(imageServiceMock, times(1)).downloadGameCoversFromIgdb();
        verify(imageServiceMock, times(1)).downloadGameScreenshotsFromIgdb();
        assertThat(result.getNewGames()).isEqualTo(lsr.getNewGames());
        assertThat(result.getDeletedGames()).isEqualTo(lsr.getDeletedGames());
        assertThat(result.getNewUnmappableFiles()).isEqualTo(lsr.getNewUnmappableFiles());
        assertThat(result.getTotalGames()).isEqualTo(lsr.getTotalGames());
        assertThat(result.getCoverDownloads()).isEqualTo(1);
        assertThat(result.getScreenshotDownloads()).isEqualTo(1);
        assertThat(result.getCompanyLogoDownloads()).isEqualTo(1);
    }


    @Test
    void downloadImages() {
        when(imageServiceMock.downloadGameCoversFromIgdb()).thenReturn(1);
        when(imageServiceMock.downloadGameScreenshotsFromIgdb()).thenReturn(1);
        when(imageServiceMock.downloadCompanyLogosFromIgdb()).thenReturn(1);

        ImageDownloadResultDto result = target.downloadImages();

        verify(imageServiceMock, times(1)).downloadCompanyLogosFromIgdb();
        verify(imageServiceMock, times(1)).downloadGameCoversFromIgdb();
        verify(imageServiceMock, times(1)).downloadGameScreenshotsFromIgdb();
        assertThat(result.getScreenshotDownloads()).isOne();
        assertThat(result.getCoverDownloads()).isOne();
        assertThat(result.getCompanyLogoDownloads()).isOne();
    }

    @Test
    void getAllFiles() {
        List<Path> gameFiles = easyRandom.objects(String.class, 5).map(Path::of).toList();

        when(libraryServiceMock.getGameFiles()).thenReturn(gameFiles);

        List<String> result = target.getAllFiles();

        verify(libraryServiceMock, times(1)).getGameFiles();
        assertThat(result).hasSameElementsAs(gameFiles.stream().map(Path::toString).toList());
    }
}
