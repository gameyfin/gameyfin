package de.grimsi.gameyfin.igdb;

import de.grimsi.gameyfin.dto.GameDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.time.Instant;
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

    private final WebClient twitchApiClient = WebClient.create();

    private WebClient igdbApiClient;

    private IgdbAccessToken accessToken;

    @PostConstruct
    public void init() {
        authenticate();
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

    private void initIgdbClient() {
        if(accessToken == null) {
            authenticate();
        }

        igdbApiClient = WebClient.builder()
                .baseUrl("https://api.igdb.com/v4/")
                .defaultHeader("Client-ID", clientId)
                .defaultHeader("Authorization", "Bearer %s".formatted(accessToken.getAccessToken()))
                .build();
    }

    public GameDto findGameByTitle(String title) {
        if (igdbApiClient == null) {
            initIgdbClient();
        }

        IgdbSearchResultDto searchResult = searchForGameByTitle(title).orElseThrow(() -> new RuntimeException("Could not find game with title : \"%s\"".formatted(title)));

        return GameDto.builder()
                .name(searchResult.getName())
                .releaseDate(Instant.ofEpochSecond(searchResult.getPublishedAt()))
                .igdbGameId(searchResult.getGame())
                .build();
    }

    public Optional<IgdbSearchResultDto> searchForGameByTitle(String searchTerm) {
        List<IgdbSearchResultDto> searchResults = new ArrayList<>();

        igdbApiClient.post()
                .uri("search")
                .bodyValue("fields *; search \"%s\"; limit 50;".formatted(searchTerm))
                .retrieve()
                .bodyToFlux(IgdbSearchResultDto.class)
                .doOnNext(searchResults::add)
                .blockLast();

        if(searchResults.isEmpty()) return Optional.empty();

        // First check if there are any matches with the exact search term
        // If no exact match has been found, check if there are matches where the name ends with the search term
        // This will filter out most DLCs and similiar stuff, but will detect a game even when your search term is not exactly the title
        //
        // Example: Searching for "Rainbow Six Siege" will result in returning "Tom Clancy's Rainbow Six Siege" (the game we want)
        //          If we just used the first result from IGDB we would get something like "Tom Clancy's Rainbow Six Siege Demon Veil" as a result

        Optional<IgdbSearchResultDto> srExactTitleMatch = searchResults.stream().filter(s -> s.getName().equals(searchTerm)).findFirst();
        if(srExactTitleMatch.isPresent()) return srExactTitleMatch;

        Optional<IgdbSearchResultDto> srTitleEndsWithMatch = searchResults.stream().filter(s -> s.getName().endsWith(searchTerm)).findFirst();
        if(srTitleEndsWithMatch.isPresent()) return srTitleEndsWithMatch;

        return Optional.of(searchResults.get(0));
    }
}
