package de.grimsi.gameyfin.mapper;

import com.igdb.proto.Igdb;
import de.grimsi.gameyfin.entities.Theme;

import java.util.List;

public class ThemeMapper {
    public static Theme toTheme(Igdb.Theme g) {
        return Theme.builder()
                .slug(g.getSlug())
                .name(g.getName())
                .build();
    }

    public static List<Theme> toThemes(List<Igdb.Theme> g) {
        return g.stream().map(ThemeMapper::toTheme).toList();
    }
}
