package de.grimsi.gameyfin.mapper;

import com.igdb.proto.Igdb;
import de.grimsi.gameyfin.entities.PlayerPerspective;

import java.util.List;

public class PlayerPerspectiveMapper {    
    
    public static PlayerPerspective toPlayerPerspective(Igdb.PlayerPerspective g) {
    return PlayerPerspective.builder()
            .slug(g.getSlug())
            .name(g.getName())
            .build();
}

    public static List<PlayerPerspective> toPlayerPerspectives(List<Igdb.PlayerPerspective> g) {
        return g.stream().map(PlayerPerspectiveMapper::toPlayerPerspective).toList();
    }
}
