package de.grimsi.gameyfin.igdb;

import java.util.List;

public class IgdbApiProperties {
    public static final String ENDPOINT_GAMES_PROTOBUF = "games.pb";
    public static final String ENDPOINT_PLATFORMS_PROTOBUF = "platforms.pb";

    private static final List<String> GAME_QUERY_FIELDS = List.of(
            "slug", "name", "summary", "first_release_date", "rating", "aggregated_rating", "total_rating", "category",
            "multiplayer_modes.lancoop", "multiplayer_modes.onlinecoop", "multiplayer_modes.offlinecoop", "multiplayer_modes.onlinemax",
            "cover.image_id", "screenshots.image_id", "videos.video_id",
            "involved_companies.company.slug", "involved_companies.company.name", "involved_companies.company.logo.image_id",
            "genres.slug", "genres.name",
            "keywords.slug", "keywords.name",
            "themes.slug", "themes.name",
            "player_perspectives.slug", "player_perspectives.name",
            "platforms.slug", "platforms.name", "platforms.platform_logo.image_id"
    );

    public static final String GAME_QUERY_FIELDS_STRING = String.join(",", GAME_QUERY_FIELDS);

    public static final String IMAGES_BASE_URL = "https://images.igdb.com/igdb/image/upload/";

    public static final String COVER_IMAGE_SIZE = "cover_big";
    public static final String SCREENSHOT_IMAGE_SIZE = "screenshot_med";
    public static final String LOGO_IMAGE_SIZE = "logo_med";

}
