package de.grimsi.gameyfin.igdb;

import com.igdb.proto.Igdb;
import de.grimsi.gameyfin.config.WebClientConfig;
import de.grimsi.gameyfin.dto.AutocompleteSuggestionDto;
import de.grimsi.gameyfin.entities.Platform;
import de.grimsi.gameyfin.igdb.IgdbApiQueryBuilder.Condition;
import de.grimsi.gameyfin.igdb.dto.TwitchOAuthTokenDto;
import de.grimsi.gameyfin.mapper.GameMapper;
import de.grimsi.gameyfin.mapper.PlatformMapper;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static de.grimsi.gameyfin.igdb.IgdbApiProperties.GAME_QUERY_FIELDS_STRING;
import static de.grimsi.gameyfin.igdb.IgdbApiQueryBuilder.*;
import static org.apache.commons.lang3.ObjectUtils.isNotEmpty;

@Slf4j
@RequiredArgsConstructor
@Service
public class IgdbWrapper {
    private final WebClient.Builder webclientBuilder;
    private final WebClientConfig webClientConfig;
    private final GameMapper gameMapper;
    @Value("${gameyfin.igdb.api.client-id}")
    private String clientId;
    @Value("${gameyfin.igdb.api.client-secret}")
    private String clientSecret;
    @Value("${gameyfin.igdb.config.preferred-platforms:6}")
    private List<Integer> preferredPlatforms;
    @Value("${gameyfin.igdb.api.endpoints.base}")
    private String igdbApiBaseUrl;
    @Value("${gameyfin.igdb.api.endpoints.auth}")
    private String twitchAuthUrl;
    private WebClient twitchApiClient;

    private WebClient igdbApiClient;

    private TwitchOAuthTokenDto accessToken;

    @PostConstruct
    public void init() {
        twitchApiClient = webclientBuilder.build();
        authenticate();
        initIgdbClient();
    }

    private void authenticate() {
        log.info("Authenticating on Twitch API...");

        URI url = UriComponentsBuilder
                .fromHttpUrl(twitchAuthUrl)
                .query("client_id={client_id}").query("client_secret={client_secret}").query("grant_type=client_credentials")
                .buildAndExpand(clientId, clientSecret)
                .toUri();

        this.accessToken = twitchApiClient
                .post()
                .uri(url)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(TwitchOAuthTokenDto.class)
                .block();

        log.info("Successfully authenticated.");
    }

    public Optional<Igdb.Game> getGameById(Long id) {
        IgdbApiQueryBuilder queryBuilder = new IgdbApiQueryBuilder();
        Igdb.GameResult gameResult = queryIgdbApi(
                IgdbApiProperties.ENDPOINT_GAMES_PROTOBUF,
                queryBuilder.fields(GAME_QUERY_FIELDS_STRING)
                        .where(equal("id", id))
                        .limit(1)
                        .build(),
                Igdb.GameResult.class
        );

        if (gameResult == null) return Optional.empty();

        return Optional.of(gameResult.getGames(0));
    }

    public Optional<Igdb.Game> getGameBySlug(String slug) {
        IgdbApiQueryBuilder queryBuilder = new IgdbApiQueryBuilder();
        Igdb.GameResult gameResult = queryIgdbApi(
                IgdbApiProperties.ENDPOINT_GAMES_PROTOBUF,
                queryBuilder.fields(GAME_QUERY_FIELDS_STRING)
                        .where(equal("slug", slug))
                        .limit(1)
                        .build(),
                Igdb.GameResult.class
        );

        if (gameResult == null) return Optional.empty();

        return Optional.of(gameResult.getGames(0));
    }

    public List<AutocompleteSuggestionDto> findPossibleMatchingTitles(String searchTerm, int limit) {
        IgdbApiQueryBuilder queryBuilder = new IgdbApiQueryBuilder();
        Igdb.GameResult gameResult = queryIgdbApi(
                IgdbApiProperties.ENDPOINT_GAMES_PROTOBUF,
                queryBuilder.search(searchTerm)
                        .fields("slug,name,first_release_date,platforms.name")
                        .where(in("platforms", preferredPlatforms))
                        .limit(limit)
                        .build(),
                Igdb.GameResult.class
        );

        if (gameResult == null) return Collections.emptyList();

        return gameResult.getGamesList().stream().map(gameMapper::toAutocompleteSuggestionDto).toList();
    }

    public Optional<Igdb.Game> searchForGameByTitle(String searchTerm) {
        return searchForGameByTitle(searchTerm, List.of());
    }

    public Optional<Igdb.Game> searchForGameByTitle(String searchTerm, Collection<String> platformSlugs) {
        IgdbApiQueryBuilder queryBuilder = new IgdbApiQueryBuilder();
        Condition platforms = isNotEmpty(platformSlugs) ?
                and(in("platforms", preferredPlatforms), in("platforms.slug", platformSlugs)) :
                in("platforms", preferredPlatforms);

        Igdb.GameResult gameResult = queryIgdbApi(
                IgdbApiProperties.ENDPOINT_GAMES_PROTOBUF,
                queryBuilder.search(searchTerm)
                        .fields(GAME_QUERY_FIELDS_STRING)
                        .where(platforms)
                        .build(),
                Igdb.GameResult.class
        );

        if (gameResult == null) {
            log.warn("Could not find game for title '{}'", searchTerm);

            // Try to remove brackets (and their content) at the end of the search term and search again
            // Although this process is recursive, we will only end up with a maximum recursion depth of two
            Pattern brackets = Pattern.compile("[()<>{}\\[\\]]");
            Matcher hasBrackets = brackets.matcher(searchTerm);

            if (hasBrackets.find()) {
                String searchTermWithoutBrackets = searchTerm.split(brackets.pattern())[0].trim();
                log.warn("Trying again with search term '{}'", searchTermWithoutBrackets);
                return searchForGameByTitle(searchTermWithoutBrackets, platformSlugs);
            }

            return Optional.empty();
        }

        List<Igdb.Game> games = gameResult.getGamesList();

        // If we only get one game, we don't have to check for exact matches, so return it directly
        if (games.size() == 1) return Optional.ofNullable(games.get(0));

        // First check if there are any matches with the exact search term
        // If no exact match has been found, check if there are matches where the name ends with the search term
        // This will filter out most DLCs and similiar stuff, but will detect a game even when your search term is not exactly the title
        // If that also returns nothing, just return the first search result
        //
        // Example: Searching for "Rainbow Six Siege" will result in returning "Tom Clancy's Rainbow Six Siege" (the game we want)
        //          If we just used the first result from IGDB we would get something like "Tom Clancy's Rainbow Six Siege Demon Veil" as a result

        Optional<Igdb.Game> srExactTitleMatch = games.stream().filter(s -> s.getName().equals(searchTerm)).findFirst();
        if (srExactTitleMatch.isPresent()) return srExactTitleMatch;

        Optional<Igdb.Game> srTitleEndsWithMatch = games.stream().filter(s -> s.getName().endsWith(searchTerm)).findFirst();
        if (srTitleEndsWithMatch.isPresent()) return srTitleEndsWithMatch;

        // Just return the first result and hope that IGDBs search algorithm is somewhat helpful this time
        return Optional.of(games.get(0));
    }

    private void initIgdbClient() {
        if (accessToken == null) {
            authenticate();
        }

        igdbApiClient = webclientBuilder
                .baseUrl(igdbApiBaseUrl)
                .defaultHeader("Client-ID", clientId)
                .defaultHeader("Authorization", "Bearer %s".formatted(accessToken.getAccessToken()))
                .filter(WebClientConfig.fixProtobufContentTypeInterceptor())
                .build();
    }

    private <T> T queryIgdbApi(String endpoint, String query, Class<T> responseClass) {
        return igdbApiClient.post()
                .uri(endpoint)
                .bodyValue(query)
                .retrieve()
                .bodyToMono(responseClass)
                .transformDeferred(BulkheadOperator.of(webClientConfig.getIgdbConcurrencyLimiter()))
                .transformDeferred(RateLimiterOperator.of(webClientConfig.getIgdbRateLimiter()))
                .block();
    }

    public List<Platform> findPlatforms(String searchTerm, int limit) {
        IgdbApiQueryBuilder queryBuilder = new IgdbApiQueryBuilder();
        Igdb.PlatformResult platformResult = queryIgdbApi(
                IgdbApiProperties.ENDPOINT_PLATFORMS_PROTOBUF,
                queryBuilder.search(searchTerm)
                        .fields("slug,name")
                        .limit(limit)
                        .build(),
                Igdb.PlatformResult.class
        );

        if (platformResult == null) return Collections.emptyList();

        return platformResult.getPlatformsList().stream().map(PlatformMapper::toPlatform).toList();
    }

    public Platform getPlatformBySlug(String slug) {
        IgdbApiQueryBuilder queryBuilder = new IgdbApiQueryBuilder();
        Igdb.PlatformResult platformResult = queryIgdbApi(
                IgdbApiProperties.ENDPOINT_PLATFORMS_PROTOBUF,
                queryBuilder.fields("slug,name,platform_logo")
                        .where(equal("slug", slug))
                        .build(),
                Igdb.PlatformResult.class
        );

        if (platformResult == null) return null;

        return platformResult.getPlatformsList().stream().map(PlatformMapper::toPlatform).findFirst().orElse(null);
    }
}
