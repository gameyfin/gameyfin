package de.grimsi.gameyfin.igdb;

import com.igdb.proto.Igdb;
import de.grimsi.gameyfin.config.WebClientConfig;
import de.grimsi.gameyfin.dto.AutocompleteSuggestionDto;
import de.grimsi.gameyfin.igdb.dto.TwitchOAuthTokenDto;
import de.grimsi.gameyfin.mapper.GameMapper;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor
@Service
public class IgdbWrapper {
    @Value("${gameyfin.igdb.api.client-id}")
    private String clientId;

    @Value("${gameyfin.igdb.api.client-secret}")
    private String clientSecret;

    @Value("${gameyfin.igdb.config.preferred-platforms:6}")
    private String preferredPlatforms;

    private final WebClient.Builder webclientBuilder;
    private final WebClientConfig webClientConfig;
    private final GameMapper gameMapper;

    private WebClient twitchApiClient;

    private WebClient igdbApiClient;

    private TwitchOAuthTokenDto accessToken;

    @PostConstruct
    public void init() {
        twitchApiClient = webclientBuilder.build();
        authenticate();
        initIgdbClient();
    }

    public void authenticate() {
        log.info("Authenticating on Twitch API...");

        URI url = UriComponentsBuilder
                .fromHttpUrl("https://id.twitch.tv/oauth2/token?client_id={client_id}&client_secret={client_secret}&grant_type=client_credentials")
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
        Igdb.GameResult gameResult = queryIgdbApi(
                IgdbApiProperties.ENPOINT_GAMES_PROTOBUF,
                "fields %s; where id = %d; limit 1;".formatted(IgdbApiProperties.GAME_QUERY_FIELDS_STRING, id),
                Igdb.GameResult.class
        );

        if (gameResult == null) return Optional.empty();

        return Optional.of(gameResult.getGames(0));
    }

    public Optional<Igdb.Game> getGameBySlug(String slug) {
        Igdb.GameResult gameResult = queryIgdbApi(
                IgdbApiProperties.ENPOINT_GAMES_PROTOBUF,
                "fields %s; where slug = \"%s\"; limit 1;".formatted(IgdbApiProperties.GAME_QUERY_FIELDS_STRING, slug),
                Igdb.GameResult.class
        );

        if (gameResult == null) return Optional.empty();

        return Optional.of(gameResult.getGames(0));
    }

    public List<AutocompleteSuggestionDto> findPossibleMatchingTitles(String searchTerm, int limit) {
        Igdb.GameResult gameResult = queryIgdbApi(
                IgdbApiProperties.ENPOINT_GAMES_PROTOBUF,
                "search \"%s\"; fields slug,name,first_release_date; where platforms = (%s); limit %d;".formatted(searchTerm, preferredPlatforms, limit),
                Igdb.GameResult.class
        );

        if(gameResult == null) return Collections.emptyList();

        return gameResult.getGamesList().stream().map(gameMapper::toAutocompleteSuggestionDto).toList();
    }

    public Optional<Igdb.Game> searchForGameByTitle(String searchTerm) {
        Igdb.GameResult gameResult = queryIgdbApi(
                IgdbApiProperties.ENPOINT_GAMES_PROTOBUF,
                "search \"%s\"; fields %s; where platforms = (%s);"
                        .formatted(searchTerm, IgdbApiProperties.GAME_QUERY_FIELDS_STRING, preferredPlatforms),
                Igdb.GameResult.class
        );

        if (gameResult == null) {
            log.warn("Could not find game for title '{}'", searchTerm);

            // Try to remove brackets (and their content) at the end of the search term and search again
            // Although this process is recursive, we will only end up with a maximum recursion depth of two
            Pattern brackets = Pattern.compile ("[()<>{}\\[\\]]");
            Matcher hasBrackets = brackets.matcher(searchTerm);

            if(hasBrackets.find()) {
                String searchTermWithoutBrackets = searchTerm.split(brackets.pattern())[0].trim();
                log.warn("Trying again with search term '{}'", searchTermWithoutBrackets);
                return searchForGameByTitle(searchTermWithoutBrackets);
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
                .baseUrl("https://api.igdb.com/v4/")
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
}
