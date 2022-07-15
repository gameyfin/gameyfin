package de.grimsi.gameyfin.mapper;

import com.igdb.proto.Igdb;
import de.grimsi.gameyfin.entities.Category;
import de.grimsi.gameyfin.entities.DetectedGame;
import de.grimsi.gameyfin.util.ProtobufUtils;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public class GameMapper {

    public static DetectedGame toDetectedGame(Igdb.Game g, Path path) {
        List<Igdb.MultiplayerMode> multiplayerModes = g.getMultiplayerModesList();
        List<Long> screenshotIds = g.getScreenshotsList().stream().map(Igdb.Screenshot::getId).toList();
        List<Long> videoIds = g.getVideosList().stream().map(Igdb.GameVideo::getId).toList();

        return DetectedGame.builder()
                .slug(g.getSlug())
                .title(g.getName())
                .summary(g.getSummary())
                .releaseDate(ProtobufUtils.toInstant(g.getFirstReleaseDate()))
                .userRating((int) g.getRating())
                .criticsRating((int) g.getAggregatedRating())
                .totalRating((int) g.getTotalRating())
                //.category()
                .offlineCoop(hasOfflineCoop(multiplayerModes))
                .onlineCoop(hasOnlineCoop(multiplayerModes))
                .lanSupport(hasLanSupport(multiplayerModes))
                .maxPlayers(getMaxPlayers(multiplayerModes))
                .coverId(g.getCover().getId())
                .screenshotIds(screenshotIds)
                .videoIds(videoIds)
                //.companies()
                //.genres()
                //.keywords()
                //.themes()
                //.playerPerspectives()
                .path(path.toString())
                .isFolder(path.toFile().isDirectory())
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
