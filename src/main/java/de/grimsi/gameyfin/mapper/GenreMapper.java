package de.grimsi.gameyfin.mapper;

import com.igdb.proto.Igdb;
import de.grimsi.gameyfin.entities.Genre;

import java.util.List;

public class GenreMapper {

    public static Genre toGenre(Igdb.Genre g) {
        return Genre.builder()
                .slug(g.getSlug())
                .name(g.getName())
                .build();
    }

    public static List<Genre> toGenres(List<Igdb.Genre> g) {
        return g.stream().map(GenreMapper::toGenre).toList();
    }
}
