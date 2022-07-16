package de.grimsi.gameyfin.igdb;

import com.igdb.proto.Igdb;
import de.grimsi.gameyfin.config.WebClientConfig;
import de.grimsi.gameyfin.igdb.dto.TwitchOAuthTokenDto;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class IgdbWrapper {

    @Value("${gameyfin.igdb.api.client-id}")
    private String clientId;

    @Value("${gameyfin.igdb.api.client-secret}")
    private String clientSecret;

    @Value("${gameyfin.igdb.config.preferred-platforms:6}")
    private String preferredPlatforms;

    @Autowired
    private WebClient.Builder webclientBuilder;

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
        Igdb.GameResult gameResult = igdbApiClient.post()
                .uri("games.pb")
                .bodyValue("fields *; where id = %d; limit 1;".formatted(id))
                .retrieve()
                .bodyToMono(Igdb.GameResult.class)
                .transformDeferred(RateLimiterOperator.of(WebClientConfig.IGDB_RATE_LIMITER))
                .block();

        if (gameResult == null) return Optional.empty();

        return Optional.of(gameResult.getGames(0));
    }

    public Optional<Igdb.Game> getGameBySlug(String slug) {
        Igdb.GameResult gameResult = igdbApiClient.post()
                .uri("games.pb")
                .bodyValue("fields *; where slug = \"%s\"; limit 1;".formatted(slug))
                .retrieve()
                .bodyToMono(Igdb.GameResult.class)
                .transformDeferred(RateLimiterOperator.of(WebClientConfig.IGDB_RATE_LIMITER))
                .block();

        if (gameResult == null) return Optional.empty();

        return Optional.of(gameResult.getGames(0));
    }

    public Optional<Igdb.Game> searchForGameByTitle(String searchTerm) {
        Igdb.GameResult gameResult = igdbApiClient.post()
                .uri("games.pb")
                .bodyValue("fields *; search \"%s\"; where platforms = (%s);".formatted(searchTerm, preferredPlatforms))
                .retrieve()
                .bodyToMono(Igdb.GameResult.class)
                .transformDeferred(RateLimiterOperator.of(WebClientConfig.IGDB_RATE_LIMITER))
                .block();

        if (gameResult == null) {
            log.warn("Could not find game for title '{}'", searchTerm);
            return Optional.empty();
        }

        List<Igdb.Game> games = gameResult.getGamesList();

        // If we only get one game, we don't have to check for exact matches, so return it directly
        if(games.size() == 1) return Optional.ofNullable(games.get(0));

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
}
