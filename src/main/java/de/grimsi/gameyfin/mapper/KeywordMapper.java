package de.grimsi.gameyfin.mapper;

import com.igdb.proto.Igdb;
import de.grimsi.gameyfin.entities.Keyword;

import java.util.List;

public class KeywordMapper {
    public static Keyword toKeyword(Igdb.Keyword g) {
        return Keyword.builder()
                .slug(g.getSlug())
                .name(g.getName())
                .build();
    }

    public static List<Keyword> toKeywords(List<Igdb.Keyword> g) {
        return g.stream().map(KeywordMapper::toKeyword).toList();
    }
}
