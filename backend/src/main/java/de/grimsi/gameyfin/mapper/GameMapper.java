package de.grimsi.gameyfin.mapper;

import com.igdb.proto.Igdb;
import de.grimsi.gameyfin.dto.GameOverviewDto;
import de.grimsi.gameyfin.entities.DetectedGame;
import de.grimsi.gameyfin.util.ProtobufUtil;

import java.nio.file.Path;
import java.util.List;

public class GameMapper {

    public static DetectedGame toDetectedGame(Igdb.Game g, Path path) {
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
                .coverId(g.getCover().getImageId())
                .screenshotIds(screenshotIds)
                .videoIds(videoIds)
                .companies(CompanyMapper.toCompanies(g.getInvolvedCompaniesList()))
                .genres(GenreMapper.toGenres(g.getGenresList()))
                .keywords(KeywordMapper.toKeywords(g.getKeywordsList()))
                .themes(ThemeMapper.toThemes(g.getThemesList()))
                .playerPerspectives(PlayerPerspectiveMapper.toPlayerPerspectives(g.getPlayerPerspectivesList()))
                .path(path.toString())
                .build();
    }

    public static GameOverviewDto toGameOverviewDto(DetectedGame game) {
        return GameOverviewDto.builder()
                .slug(game.getSlug())
                .title(game.getTitle())
                .coverId(game.getCoverId())
                .build();
    }

    private static boolean hasOfflineCoop(List<Igdb.MultiplayerMode> modes) {
        return modes.stream().anyMatch(Igdb.MultiplayerMode::getOfflinecoop);
    }

    private static boolean hasLanSupport(List<Igdb.MultiplayerMode> modes) {
        return modes.stream().anyMatch(Igdb.MultiplayerMode::getLancoop);
    }

    private static boolean hasOnlineCoop(List<Igdb.MultiplayerMode> modes) {
        return modes.stream().anyMatch(Igdb.MultiplayerMode::getOnlinecoop);
    }

    private static int getMaxPlayers(List<Igdb.MultiplayerMode> modes) {
        return modes.stream().mapToInt(Igdb.MultiplayerMode::getOnlinecoopmax).max().orElse(0);
    }
}
