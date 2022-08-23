package de.grimsi.gameyfin.mapper;

import com.igdb.proto.Igdb;
import de.grimsi.gameyfin.dto.AutocompleteSuggestionDto;
import de.grimsi.gameyfin.dto.GameOverviewDto;
import de.grimsi.gameyfin.entities.DetectedGame;
import de.grimsi.gameyfin.service.FilesystemService;
import de.grimsi.gameyfin.service.LibraryService;
import de.grimsi.gameyfin.util.ProtobufUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@RequiredArgsConstructor
@Component
public class GameMapper {

    private final FilesystemService filesystemService;

    public DetectedGame toDetectedGame(Igdb.Game g, Path path) {
        List<Igdb.MultiplayerMode> multiplayerModes = g.getMultiplayerModesList();
        List<String> screenshotIds = g.getScreenshotsList().stream().map(Igdb.Screenshot::getImageId).toList();
        List<String> videoIds = g.getVideosList().stream().map(Igdb.GameVideo::getVideoId).toList();

        return DetectedGame.builder()
                .slug(g.getSlug())
                .title(g.getName())
                .summary(g.getSummary())
                .releaseDate(ProtobufUtil.toInstant(g.getFirstReleaseDate()))
                .userRating((int) g.getRating())
                .criticsRating((int) g.getAggregatedRating())
                .totalRating((int) g.getTotalRating())
                .category(g.getCategory().name())
                .offlineCoop(hasOfflineCoop(multiplayerModes))
                .onlineCoop(hasOnlineCoop(multiplayerModes))
                .lanSupport(hasLanSupport(multiplayerModes))
                .maxPlayers(getMaxPlayers(multiplayerModes))
                .coverId(getCoverId(g))
                .screenshotIds(screenshotIds)
                .videoIds(videoIds)
                .companies(CompanyMapper.toCompanies(g.getInvolvedCompaniesList()))
                .genres(GenreMapper.toGenres(g.getGenresList()))
                .keywords(KeywordMapper.toKeywords(g.getKeywordsList()))
                .themes(ThemeMapper.toThemes(g.getThemesList()))
                .playerPerspectives(PlayerPerspectiveMapper.toPlayerPerspectives(g.getPlayerPerspectivesList()))
                .path(path.toString())
                .diskSize(calculateDiskSize(g, path))
                .addedToLibrary(Instant.now())
                .build();
    }

    public GameOverviewDto toGameOverviewDto(DetectedGame game) {
        return GameOverviewDto.builder()
                .slug(game.getSlug())
                .title(game.getTitle())
                .coverId(game.getCoverId())
                .build();
    }

    public AutocompleteSuggestionDto toAutocompleteSuggestionDto(Igdb.Game game) {
        return AutocompleteSuggestionDto.builder()
                .slug(game.getSlug())
                .title(game.getName())
                .releaseDate(ProtobufUtil.toInstant(game.getFirstReleaseDate()))
                .build();
    }

    private String getCoverId(Igdb.Game g) {
        String coverId = g.getCover().getImageId();

        if(StringUtils.hasText(coverId)) return coverId;

        return "nocover";
    }

    private boolean hasOfflineCoop(List<Igdb.MultiplayerMode> modes) {
        return modes.stream().anyMatch(Igdb.MultiplayerMode::getOfflinecoop);
    }

    private boolean hasLanSupport(List<Igdb.MultiplayerMode> modes) {
        return modes.stream().anyMatch(Igdb.MultiplayerMode::getLancoop);
    }

    private boolean hasOnlineCoop(List<Igdb.MultiplayerMode> modes) {
        return modes.stream().anyMatch(Igdb.MultiplayerMode::getOnlinecoop);
    }

    private int getMaxPlayers(List<Igdb.MultiplayerMode> modes) {
        return modes.stream().mapToInt(Igdb.MultiplayerMode::getOnlinecoopmax).max().orElse(0);
    }

    private long calculateDiskSize(Igdb.Game g, Path path) {
        StopWatch stopWatch = new StopWatch();
        log.info("Calculating disk size for game '{}'...", g.getName());

        stopWatch.start();

        long fileSize;

        try {
            fileSize = filesystemService.getSizeOnDisk(path);
        } catch(IOException e) {
            log.error("Error while calculating disk size for game '{}'", g.getName());
            fileSize = -1L;
        }

        stopWatch.stop();

        log.info("Calculated disk size for game '{}' in {} seconds", g.getName(), (int) stopWatch.getTotalTimeSeconds());
        return fileSize;
    }
}
