package de.grimsi.gameyfin.mapper;

import com.igdb.proto.Igdb;
import de.grimsi.gameyfin.entities.Platform;

import java.util.List;

public class PlatformMapper {

    public static Platform toPlatform(Igdb.Platform c) {
        return Platform.builder()
                .slug(c.getSlug())
                .name(c.getName())
                .logoId(c.getPlatformLogo().getImageId())
                .build();
    }

    public static List<Platform> toPlatforms(List<Igdb.Platform> c) {
        return c.stream().map(PlatformMapper::toPlatform).toList();
    }
}
