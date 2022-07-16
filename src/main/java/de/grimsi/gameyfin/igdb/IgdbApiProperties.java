package de.grimsi.gameyfin.igdb;

import java.util.List;

public class IgdbApiProperties {
    public static final String IGDB_ENPOINT_GAMES_PROTOBUF = "games.pb";

    public static final List<String> GAME_QUERY_FIELDS = List.of(
            "slug", "name", "summary", "first_release_date", "rating", "aggregated_rating", "total_rating", "category", "multiplayer_modes", "cover", "screenshots", "videos", // All top-level fields
            "involved_companies.company.slug", "involved_companies.company.name", "involved_companies.company.logo.id",
            "genres.slug", "genres.name",
            "keywords.slug", "keywords.name",
            "themes.slug", "themes.name",
            "player_perspectives.slug", "player_perspectives.name"
    );

}
