package de.grimsi.gameyfin.igdb;

import de.grimsi.gameyfin.igdb.dto.IgdbAccessToken;
import de.grimsi.gameyfin.igdb.dto.IgdbGame;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.netty.http.client.HttpClient;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
public class IgdbWrapper {

    @Value("${gameyfin.igdb.api.client-id}")
    private String clientId;

    @Value("${gameyfin.igdb.api.client-secret}")
    private String clientSecret;

    @Value("${gameyfin.igdb.config.preferred-platform}")
    private int preferredPlatform;

    private final WebClient twitchApiClient = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(HttpClient.create().proxyWithSystemProperties()))
            .build();

    private WebClient igdbApiClient;

    private IgdbAccessToken accessToken;

    @PostConstruct
    public void init() {
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
                .bodyToMono(IgdbAccessToken.class)
                .block();

        log.info("Successfully authenticated.");
    }

    public IgdbGame findGameByTitle(String title) {
        return searchForGameByTitle(title).orElseThrow(() -> new RuntimeException("Could not find game with title: \"%s\"".formatted(title)));
    }

    private Optional<IgdbGame> getGameById(Long id) {
        return Optional.ofNullable(
                igdbApiClient.post()
                        .uri("games")
                        .bodyValue("fields *; where id = %d;".formatted(id))
                        .retrieve()
                        .bodyToMono(IgdbGame.class)
                        .block()
        );
    }

    private void initIgdbClient() {
        if (accessToken == null) {
            authenticate();
        }

        igdbApiClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create().proxyWithSystemProperties()))
                .baseUrl("https://api.igdb.com/v4/")
                .defaultHeader("Client-ID", clientId)
                .defaultHeader("Authorization", "Bearer %s".formatted(accessToken.getAccessToken()))
                .build();
    }

    private Optional<IgdbGame> searchForGameByTitle(String searchTerm) {
        List<IgdbGame> games = new ArrayList<>();

        igdbApiClient.post()
                .uri("games")
                .bodyValue("fields *; search \"%s\";".formatted(searchTerm))
                .retrieve()
                .bodyToFlux(IgdbGame.class)
                .doOnNext(games::add)
                .blockLast();

        if (games.isEmpty()) return Optional.empty();

        // First check if there are any matches with the exact search term
        // If no exact match has been found, check if there are matches where the name ends with the search term
        // This will filter out most DLCs and similiar stuff, but will detect a game even when your search term is not exactly the title
        // If that also returns nothing, just return the first search result
        //
        // Example: Searching for "Rainbow Six Siege" will result in returning "Tom Clancy's Rainbow Six Siege" (the game we want)
        //          If we just used the first result from IGDB we would get something like "Tom Clancy's Rainbow Six Siege Demon Veil" as a result

        Optional<IgdbGame> srExactTitleMatch = games.stream().filter(s -> s.getName().equals(searchTerm)).findFirst();
        if (srExactTitleMatch.isPresent()) return srExactTitleMatch;

        Optional<IgdbGame> srTitleEndsWithMatch = games.stream().filter(s -> s.getName().endsWith(searchTerm)).findFirst();
        if (srTitleEndsWithMatch.isPresent()) return srTitleEndsWithMatch;

        return Optional.of(games.get(0));
    }
}
